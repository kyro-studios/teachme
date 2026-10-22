// 授業の進行の状態機械 (architecture 5.2)。この reducer は副作用を持たず、録音、API、音声、ボードは画面の側が行う。
// 状態の名前と遷移は、仕様書 7.3 と architecture 5.2 に合わせる。まとめ (`summarizing`) は、fe-lesson-summary で足す。
import type { components } from "../../api/schema";
import type { AvatarExpression, AvatarGesture } from "../../shared/avatar/avatar-model";

type Expression = components["schemas"]["Expression"];
type StudentAnnotation = components["schemas"]["StudentAnnotation"];
type Turn = components["schemas"]["Turn"];
type LessonReply = components["schemas"]["LessonReply"];

/** 認識が空か失敗したとき (仕様書 5.5) */
export const RECOGNITION_FAILED_MESSAGE = "うまく聞き取れませんでした。もう一度話してください";

/** 生徒の応答が失敗したとき (仕様書 5.5) */
export const REPLY_FAILED_MESSAGE = "生徒の応答を作れませんでした。もう一度話してください";

/** 録音の準備や、録音の後の処理が失敗したとき (文言は仕様書に無いため、この文書で決めた) */
export const RECORDING_FAILED_MESSAGE = "録音できませんでした。もう一度試してください";

/** チャイムの文言 (仕様書 5.5、10 節) */
export const CHIME_MESSAGE = "チャイムが鳴りました。授業のまとめに入ります";

/** 相槌 (仕様書 5.5)。認識の結果を受けた直後に、1 つだけ出す。字幕には残さない */
export const FILLERS = ["えーっと…", "なるほど！", "ふむ…"] as const;

export type Speaker = "you" | "student";

/** 字幕の 1 件。話し手の表示名は、画面が決める */
export type Subtitle = { speaker: Speaker; text: string };

/** AI 生徒の 1 回の発話 (開始の発話か応答)。字幕、表情、印、音声の材料 */
export type Speech = {
  turnId: string;
  text: string;
  expression: Expression;
  annotation: StudentAnnotation | null;
  /** 応答の時点で、授業の上限 (往復か期限) に達した */
  limitReached: boolean;
};

/** どの状態でも持つ値。`seq` は、状態が変わるたびに増え、画面の効果 (音声、録音、API) を 1 回だけ行う印になる */
type Shared = {
  seq: number;
  subtitles: readonly Subtitle[];
  expression: AvatarExpression;
  gesture: AvatarGesture;
  /** 相槌。応答を待つ間だけ出す */
  filler: string | null;
  /** 画面に出す文言 (認識、応答、チャイムの失敗) */
  notice: string | null;
  /** 期限に達した。応答の字幕と音声が終わってから、チャイムを鳴らす (保留中のときは、字幕を出してすぐ) */
  timeUp: boolean;
  /** 字幕として出した AI 生徒の発話の印。ボードに印を書き込むときの目印 (出すたびに新しい値) */
  shown: { annotation: StudentAnnotation | null } | null;
};

type Phased =
  | { phase: "opening"; speech: Speech }
  | { phase: "idle" }
  | { phase: "recording" }
  | { phase: "transcribing" }
  | { phase: "awaitingReply"; utteranceId: string }
  | { phase: "holding"; speech: Speech }
  | { phase: "speaking"; speech: Speech }
  | { phase: "chime" };

export type LessonState = Shared & Phased;

export type LessonEvent =
  /** 「話す」。発話待ちのときだけ受け付ける */
  | { type: "talk" }
  /** 録音が終わった (「話し終える」、60 秒、5 MB)。ボードの画像も用意できている */
  | { type: "recorded" }
  | { type: "recordingFailed"; notice: string }
  /** 認識できた */
  | { type: "recognized"; utteranceId: string; text: string; filler: string }
  /** 認識が空か、失敗した */
  | { type: "recognitionFailed" }
  /** 上限に達していた (409 `limit_reached`)。応答を出さずに、チャイムへ */
  | { type: "limitReached" }
  /** 応答と学習評価が終わった */
  | { type: "replied"; speech: Speech }
  | { type: "replyFailed" }
  /** ペンが離れてから 2 秒たった (書き込み中ではない) */
  | { type: "penReady" }
  /** 字幕と音声が終わった。音声が出せなかったときも、この事象で続ける */
  | { type: "speechEnded" }
  /** 時計が期限に達した */
  | { type: "timeUp" };

/** 授業の開始の発話から、最初の状態を作る。開始の発話は往復に数えない */
export function createLessonState(opening: Speech): LessonState {
  return {
    seq: 0,
    subtitles: [{ speaker: "student", text: opening.text }],
    expression: opening.expression,
    gesture: "talk",
    filler: null,
    notice: null,
    timeUp: false,
    shown: null,
    phase: "opening",
    speech: opening,
  };
}

/** 開始の結果の発話 (`LessonStart.opening`) を、状態の発話にする */
export function openingSpeech(turn: Turn): Speech {
  return {
    turnId: turn.turnId,
    text: turn.text,
    expression: turn.expression ?? "normal",
    annotation: null,
    limitReached: false,
  };
}

/** 応答の結果 (`LessonReply`) を、状態の発話にする */
export function replySpeech(reply: LessonReply): Speech {
  return {
    turnId: reply.turn.turnId,
    text: reply.turn.text,
    expression: reply.turn.expression ?? "normal",
    annotation: reply.annotation,
    limitReached: reply.limitReached,
  };
}

/** 相槌を 1 つ選ぶ。`random` は、テストのために差し替えられる */
export function pickFiller(random: () => number = Math.random): string {
  return FILLERS[Math.floor(random() * FILLERS.length)];
}

function sharedOf(state: LessonState): Shared {
  return {
    seq: state.seq,
    subtitles: state.subtitles,
    expression: state.expression,
    gesture: state.gesture,
    filler: state.filler,
    notice: state.notice,
    timeUp: state.timeUp,
    shown: state.shown,
  };
}

/** フェーズが変わるときの共通の処理。`seq` を進める */
function enter(state: LessonState, patch: Partial<Shared> = {}): Shared {
  return { ...sharedOf(state), ...patch, seq: state.seq + 1 };
}

/** 期限に達しているときは、チャイムへ。そうでなければ発話待ちへ戻す */
function endTurn(state: LessonState, patch: Partial<Shared> = {}): LessonState {
  if (state.timeUp) {
    return toChime(state, patch);
  }
  return { ...enter(state, { ...patch, filler: null }), phase: "idle" };
}

function toChime(state: LessonState, patch: Partial<Shared> = {}): LessonState {
  return { ...enter(state, { ...patch, filler: null, notice: CHIME_MESSAGE }), phase: "chime" };
}

/** 授業の進行の遷移。仕様書 7.3 と architecture 5.2 の規則に従い、合わない事象は無視する */
export function lessonReducer(state: LessonState, event: LessonEvent): LessonState {
  switch (event.type) {
    case "talk":
      // 「話す」は発話待ちのときだけ。押し直しで録音が二重に始まらないようにする
      return state.phase === "idle"
        ? { ...enter(state, { notice: null }), phase: "recording" }
        : state;

    case "recorded":
      return state.phase === "recording" ? { ...enter(state), phase: "transcribing" } : state;

    case "recordingFailed":
      return state.phase === "recording" ? endTurn(state, { notice: event.notice }) : state;

    case "recognized":
      if (state.phase !== "transcribing") {
        return state;
      }
      return {
        ...enter(state, {
          subtitles: [...state.subtitles, { speaker: "you", text: event.text }],
          expression: "thinking",
          gesture: "think",
          filler: event.filler,
          notice: null,
        }),
        phase: "awaitingReply",
        utteranceId: event.utteranceId,
      };

    case "recognitionFailed":
      return state.phase === "transcribing"
        ? endTurn(state, { notice: RECOGNITION_FAILED_MESSAGE, expression: "normal" })
        : state;

    case "limitReached":
      // 録音は捨てて (認識も応答もしない)、チャイムへ
      return state.phase === "transcribing" || state.phase === "awaitingReply"
        ? toChime(state)
        : state;

    case "replied":
      // 応答が来ても、すぐには出さない。ペンの書き込みが終わるまで待つ (holding)
      if (state.phase !== "awaitingReply") {
        return state;
      }
      return {
        ...enter(state, { expression: "normal", gesture: "nod", filler: null }),
        phase: "holding",
        speech: event.speech,
      };

    case "replyFailed":
      return state.phase === "awaitingReply"
        ? endTurn(state, { notice: REPLY_FAILED_MESSAGE, expression: "normal" })
        : state;

    case "penReady":
      if (state.phase !== "holding") {
        return state;
      }
      return {
        ...enter(state, {
          subtitles: [...state.subtitles, { speaker: "student", text: state.speech.text }],
          expression: state.speech.expression,
          gesture: "talk",
          shown: { annotation: state.speech.annotation },
        }),
        phase: "speaking",
        speech: state.speech,
      };

    case "speechEnded":
      if (state.phase !== "opening" && state.phase !== "speaking") {
        return state;
      }
      return state.speech.limitReached ? toChime(state) : endTurn(state, { gesture: "nod" });

    case "timeUp":
      switch (state.phase) {
        case "idle":
        case "recording":
          return toChime(state);
        case "holding":
          // 保留中の応答は、音声なしで字幕だけ出してから、チャイムへ (architecture 5.2)
          return toChime(state, {
            subtitles: [...state.subtitles, { speaker: "student", text: state.speech.text }],
            expression: state.speech.expression,
            gesture: "nod",
            shown: { annotation: state.speech.annotation },
            timeUp: true,
          });
        case "chime":
          return state;
        default:
          // 開始の発話、認識、応答、発話の途中は、終わるまで待ってからチャイムにする
          return state.timeUp ? state : { ...state, timeUp: true };
      }

    default:
      return state;
  }
}
