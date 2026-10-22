// 授業の進行の副作用。状態機械 (lesson-state) の状態を見て、録音、API、音声、ボード、時計を動かし、結果を事象として返す
import { useQuery } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, useState, type RefObject } from "react";
import { ApiError } from "../../api/client";
import { configQuery } from "../../api/queries";
import { playChime } from "../../shared/audio/chime";
import { playAudio } from "../../shared/audio/player";
import { RecordingError, startRecording, type Recording } from "../../shared/audio/recorder";
import type { BoardHandle, Region } from "../../shared/board";
import {
  abandonLesson,
  lessonAudioUrl,
  replyToUtterance,
  sendUtterance,
  type LessonEntry,
} from "./lesson-api";
import {
  createLessonState,
  lessonReducer,
  openingSpeech,
  pickFiller,
  RECORDING_FAILED_MESSAGE,
  replySpeech,
  type LessonState,
} from "./lesson-state";
import { clockOffset, penWaitMs, PEN_POLL_MS, remainingUntil } from "./lesson-timing";

/** 1 回の発話の入力。認識に録音を、応答にボードの画像と指している範囲を使う */
type Utterance = { blob: Blob; image: Blob; pointedRegions: Region[] };

/**
 * 授業の進行。状態と、「話す」の操作を返す。
 * 期限は、サーバーの時計に合わせた端末の時計で数え、達したら `timeUp` を送る。
 * 画面を離れたときは、`keepalive` で中断を送る。
 */
export function useLessonSession(entry: LessonEntry, boardRef: RefObject<BoardHandle | null>) {
  const aiMode = useQuery(configQuery).data?.aiMode ?? "real";
  const [state, dispatch] = useReducer(lessonReducer, entry, (start) =>
    createLessonState(openingSpeech(start.opening)),
  );
  const [offset] = useState(() => clockOffset(entry.serverNow, Date.now()));

  const recordingRef = useRef<Promise<Recording> | null>(null);
  const utteranceRef = useRef<Utterance | null>(null);
  const placedRef = useRef<LessonState["shown"]>(null);
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // 期限。達したら、状態ごとの扱いは reducer が決める
  useEffect(() => {
    const timer = setTimeout(
      () => dispatch({ type: "timeUp" }),
      remainingUntil(entry.deadlineAt, offset, Date.now()),
    );
    return () => clearTimeout(timer);
  }, [entry.deadlineAt, offset]);

  // 開始の発話と、AI 生徒の応答の音声。音声が出せないときも、字幕だけで続ける
  useEffect(() => {
    if (state.phase !== "opening" && state.phase !== "speaking") {
      return;
    }
    const playback = playAudio(lessonAudioUrl(entry.lessonId, state.speech.turnId), {
      onEnd: () => dispatch({ type: "speechEnded" }),
      onError: () => dispatch({ type: "speechEnded" }),
    });
    return () => playback.stop();
  }, [state.seq, entry.lessonId]);

  // AI 生徒の印は、字幕と同時に 1 回だけ書く
  useEffect(() => {
    if (!state.shown || placedRef.current === state.shown) {
      return;
    }
    placedRef.current = state.shown;
    if (state.shown.annotation) {
      boardRef.current?.addStudentAnnotation(state.shown.annotation);
    }
  }, [state.shown, boardRef]);

  // 応答が来ても、ペンが触れている間と、離してから 2 秒は待つ
  useEffect(() => {
    if (state.phase !== "holding") {
      return;
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const check = () => {
      const board = boardRef.current;
      const wait = board
        ? penWaitMs(
            { isWriting: board.isWriting(), releasedAt: board.lastReleasedAt() },
            Date.now(),
          )
        : 0;
      if (wait === 0) {
        dispatch({ type: "penReady" });
        return;
      }
      timer = setTimeout(check, wait ?? PEN_POLL_MS);
    };
    check();
    return () => clearTimeout(timer);
  }, [state.seq, boardRef]);

  // 録音。終わったら、ボードの画像と指している範囲を用意してから、認識へ進む
  useEffect(() => {
    if (state.phase !== "recording") {
      return;
    }
    const startedAt = Date.now();
    let active = true;
    const starting = startRecording({ aiMode });
    recordingRef.current = starting;
    starting
      .then(async (recording) => {
        const { blob } = await recording.finished;
        const endedAt = Date.now();
        const board = boardRef.current;
        if (!active) {
          return;
        }
        if (!board) {
          throw new Error("ボードの準備ができていません");
        }
        const image = await board.exportPng();
        const pointedRegions = board.pointedRegions({ startedAt, endedAt });
        if (!active) {
          return;
        }
        utteranceRef.current = { blob, image, pointedRegions };
        dispatch({ type: "recorded" });
      })
      .catch((error: unknown) => {
        if (active) {
          dispatch({ type: "recordingFailed", notice: recordingNotice(error) });
        }
      });
    return () => {
      // 状態が変わった (チャイムなど) ときは、録音を止め、結果を捨てる
      active = false;
      void starting.then(
        (recording) => recording.stop(),
        () => undefined,
      );
    };
  }, [state.seq, aiMode, boardRef]);

  // 認識 (STT)。応答は作らない
  useEffect(() => {
    const utterance = utteranceRef.current;
    if (state.phase !== "transcribing" || !utterance) {
      return;
    }
    let active = true;
    sendUtterance(entry.lessonId, utterance.blob).then(
      (result) => {
        if (!active) {
          return;
        }
        if (result.recognized && result.utteranceId && result.text) {
          dispatch({
            type: "recognized",
            utteranceId: result.utteranceId,
            text: result.text,
            filler: pickFiller(),
          });
        } else {
          dispatch({ type: "recognitionFailed" });
        }
      },
      (error: unknown) => {
        if (active) {
          dispatch(
            isLimitReached(error) ? { type: "limitReached" } : { type: "recognitionFailed" },
          );
        }
      },
    );
    return () => {
      active = false;
    };
  }, [state.seq, entry.lessonId]);

  // 応答と学習評価 (LLM)。結果が来るまで、字幕は出さない
  useEffect(() => {
    const utterance = utteranceRef.current;
    if (state.phase !== "awaitingReply" || !utterance) {
      return;
    }
    const { image, pointedRegions } = utterance;
    let active = true;
    replyToUtterance(entry.lessonId, state.utteranceId, image, pointedRegions).then(
      (reply) => {
        if (active) {
          dispatch({ type: "replied", speech: replySpeech(reply) });
        }
      },
      (error: unknown) => {
        if (active) {
          dispatch(isLimitReached(error) ? { type: "limitReached" } : { type: "replyFailed" });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [state.seq, entry.lessonId]);

  // チャイムの音。書き込み中は鳴らさない (architecture 5.2)
  useEffect(() => {
    if (state.phase === "chime" && !boardRef.current?.isWriting()) {
      playChime();
    }
  }, [state.seq, boardRef]);

  // 画面を離れると、授業を中断する。開発時の二重の実行では、直前の中断を取り消す
  useEffect(() => {
    clearTimeout(leaveTimerRef.current);
    const leave = () => {
      void abandonLesson(entry.lessonId, { keepalive: true }).catch(() => undefined);
    };
    window.addEventListener("pagehide", leave);
    return () => {
      window.removeEventListener("pagehide", leave);
      leaveTimerRef.current = setTimeout(leave, 0);
    };
  }, [entry.lessonId]);

  /** 「話す」と「話し終える」。同じボタンで、状態に応じて働く */
  const pressTalk = () => {
    if (state.phase === "idle") {
      dispatch({ type: "talk" });
    } else if (state.phase === "recording") {
      void recordingRef.current?.then(
        (recording) => recording.stop(),
        () => undefined,
      );
    }
  };

  return { state, pressTalk };
}

function isLimitReached(error: unknown): boolean {
  return error instanceof ApiError && error.code === "limit_reached";
}

function recordingNotice(error: unknown): string {
  return error instanceof RecordingError ? error.message : RECORDING_FAILED_MESSAGE;
}
