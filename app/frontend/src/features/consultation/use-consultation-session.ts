// 相談の進行の副作用。状態機械 (consultation-state) の状態を見て、録音、API、音声、ボード、時計を動かし、結果を事象として返す
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, type RefObject } from "react";
import { ApiError } from "../../api/client";
import { configQuery } from "../../api/queries";
import { playAudio, type Playback } from "../../shared/audio/player";
import { RecordingError, startRecording, type Recording } from "../../shared/audio/recorder";
import type { BoardHandle, Region } from "../../shared/board";
import {
  abandonConsultation,
  forgetConsultationEntry,
  loadConsultationAudio,
  replyToMentor,
  sendUtterance,
  type ConsultationEntry,
} from "./consultation-api";
import {
  consultationReducer,
  createConsultationState,
  RECORDING_FAILED_MESSAGE,
  replySpeech,
  type ConsultationState,
} from "./consultation-state";
import { penWaitMs, PEN_POLL_MS, remainingUntil } from "./consultation-timing";

/** 1 回の発話の入力。認識に録音を、応答にボードの画像と指している範囲を使う */
type Utterance = { blob: Blob; image: Blob; pointedRegions: Region[] };

/**
 * 相談の進行。状態と、「話す」の操作を返す。
 * 期限は、サーバーの時計に合わせた端末の時計で数え、達したら `timeUp` を送る。
 * 画面を離れたときは、`keepalive` で中断を送る。
 */
export function useConsultationSession(
  entry: ConsultationEntry,
  boardRef: RefObject<BoardHandle | null>,
) {
  const aiMode = useQuery(configQuery).data?.aiMode ?? "real";
  const [state, dispatch] = useReducer(consultationReducer, undefined, createConsultationState);
  const queryClient = useQueryClient();

  const recordingRef = useRef<Promise<Recording> | null>(null);
  const utteranceRef = useRef<Utterance | null>(null);
  const placedRef = useRef<ConsultationState["shown"]>(null);
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const audioRef = useRef<{ turnId: string; src: Promise<string> } | null>(null);

  // 期限。達したら、状態ごとの扱いは reducer が決める
  useEffect(() => {
    const timer = setTimeout(
      () => dispatch({ type: "timeUp" }),
      remainingUntil(entry.deadlineAt, entry.clockOffset, Date.now()),
    );
    return () => clearTimeout(timer);
  }, [entry.deadlineAt, entry.clockOffset]);

  // 先輩先生の応答の音声。応答が届いた時点 (holding) から取り始め、ペンの待ちと生成を重ねる。1 つの応答につき 1 回だけ取る
  const audioSourceFor = (turnId: string): Promise<string> => {
    if (audioRef.current?.turnId !== turnId) {
      audioRef.current = {
        turnId,
        src: loadConsultationAudio(entry.consultationId, turnId),
      };
    }
    return audioRef.current.src;
  };
  useEffect(() => {
    if (state.phase === "holding") {
      void audioSourceFor(state.speech.turnId);
    }
  }, [state.seq, entry.consultationId]);

  // 応答の再生。字幕を出すときに始める。音声が出せないときも、字幕だけで続ける
  useEffect(() => {
    if (state.phase !== "speaking") {
      return;
    }
    let playback: Playback | undefined;
    let active = true;
    void audioSourceFor(state.speech.turnId).then((src) => {
      if (!active) {
        return;
      }
      playback = playAudio(src, {
        onEnd: () => {
          releaseAudio(src);
          dispatch({ type: "speechEnded" });
        },
        onError: () => {
          releaseAudio(src);
          dispatch({ type: "speechEnded" });
        },
      });
    });
    return () => {
      active = false;
      playback?.stop();
    };
  }, [state.seq, entry.consultationId]);

  // 先輩先生の書き込みは、字幕と同時に 1 回だけ書く
  useEffect(() => {
    if (!state.shown || placedRef.current === state.shown) {
      return;
    }
    placedRef.current = state.shown;
    if (state.shown.boardWrites.length > 0) {
      boardRef.current?.addBoardWrites(state.shown.boardWrites);
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
      // 状態が変わった (区切りなど) ときは、録音を止め、結果を捨てる
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
    sendUtterance(entry.consultationId, utterance.blob).then(
      (result) => {
        if (!active) {
          return;
        }
        if (result.recognized && result.utteranceId && result.text) {
          dispatch({
            type: "recognized",
            utteranceId: result.utteranceId,
            text: result.text,
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
  }, [state.seq, entry.consultationId]);

  // 先輩先生の応答 (LLM)。結果が来るまで、字幕は出さない
  useEffect(() => {
    const utterance = utteranceRef.current;
    if (state.phase !== "awaitingReply" || !utterance) {
      return;
    }
    const { image, pointedRegions } = utterance;
    let active = true;
    replyToMentor(entry.consultationId, state.utteranceId, image, pointedRegions).then(
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
  }, [state.seq, entry.consultationId]);

  // 画面を離れると、相談を中断する。開発時の二重の実行では、直前の中断を取り消す
  useEffect(() => {
    clearTimeout(leaveTimerRef.current);
    const leave = () => {
      // 中断した相談の開始の結果は捨てる。戻る、進むで来ても、中断された相談は出さない
      forgetConsultationEntry(queryClient, entry.materialId);
      void abandonConsultation(entry.consultationId, { keepalive: true }).catch(() => undefined);
    };
    window.addEventListener("pagehide", leave);
    return () => {
      window.removeEventListener("pagehide", leave);
      leaveTimerRef.current = setTimeout(leave, 0);
    };
  }, [entry.consultationId, entry.materialId, queryClient]);

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

/** 音声が URL の作成 (blob:) だったときは、取り消す。外部の URL には何もしない */
function releaseAudio(src: string): void {
  if (src.startsWith("blob:")) {
    URL.revokeObjectURL(src);
  }
}

function isLimitReached(error: unknown): boolean {
  return error instanceof ApiError && error.code === "limit_reached";
}

function recordingNotice(error: unknown): string {
  return error instanceof RecordingError ? error.message : RECORDING_FAILED_MESSAGE;
}
