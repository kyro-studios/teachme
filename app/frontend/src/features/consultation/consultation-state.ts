// 先輩先生との相談の進行の状態機械 (architecture 5.2)。授業と同じ形で、学習評価、まとめ、最初の発話を持たない。
// この reducer は副作用を持たず、録音、API、音声、ボード、時計は画面の側が行う。
import type { components } from "../../api/schema";
import type { AvatarExpression, AvatarGesture } from "../../shared/avatar/avatar-model";

type Expression = components["schemas"]["Expression"];
type BoardWrite = components["schemas"]["BoardWrite"];
type ConsultationReply = components["schemas"]["ConsultationReply"];

/** 認識が空か失敗したとき (仕様書 5.4、10 節。相談と授業で同じ文言) */
export const RECOGNITION_FAILED_MESSAGE = "うまく聞き取れませんでした。もう一度話してください";

/** 先輩先生の応答が失敗したとき (仕様書 5.4) */
export const REPLY_FAILED_MESSAGE = "先輩先生の応答を作れませんでした。もう一度話してください";

/** 録音の準備や、録音の後の処理が失敗したとき (文言は仕様書に無いため、授業と同じ文言にする) */
export const RECORDING_FAILED_MESSAGE = "録音できませんでした。もう一度試してください";

/** 上限に達して区切るときの文言 (仕様書 5.4、10 節)。「話す」は押せなくなる */
export const ENDED_MESSAGE = "相談の時間が終わりました";

/** 字幕の話し手。利用者と先輩先生 */
export type Speaker = "you" | "mentor";

/** 字幕の 1 件。表示名は、画面が決める */
export type Subtitle = { speaker: Speaker; text: string };

/** 先輩先生の 1 回の応答。字幕、表情、ボードへの書き込み、上限の判定の材料 */
export type Speech = {
  turnId: string;
  text: string;
  expression: Expression;
  boardWrites: readonly BoardWrite[];
  /** 応答の時点で、相談の上限 (往復か期限) に達した */
  limitReached: boolean;
};

/** どの状態でも持つ値。`seq` は、状態が変わるたびに増え、画面の効果 (音声、録音、API) を 1 回だけ行う印になる */
type Shared = {
  seq: number;
  subtitles: readonly Subtitle[];
  expression: AvatarExpression;
  gesture: AvatarGesture;
  /** 画面に出す文言 (認識、応答、上限の失敗) */
  notice: string | null;
  /** 期限に達した。応答の字幕と音声が終わってから区切る (保留中のときは、字幕を出してすぐ) */
  timeUp: boolean;
  /** 字幕として出した先輩先生の応答の書き込み。ボードに書き込むときの目印 (出すたびに新しい値) */
  shown: { boardWrites: readonly BoardWrite[] } | null;
};

type Phased =
  | { phase: "idle" }
  | { phase: "recording" }
  | { phase: "transcribing" }
  | { phase: "awaitingReply"; utteranceId: string }
  | { phase: "holding"; speech: Speech }
  | { phase: "speaking"; speech: Speech }
  /** 相談の上限に達して区切った。「話す」は押せない */
  | { phase: "ended" };

export type ConsultationState = Shared & Phased;

export type ConsultationEvent =
  /** 「話す」。発話待ちのときだけ受け付ける */
  | { type: "talk" }
  /** 録音が終わった (「話し終える」、60 秒、5 MB)。ボードの画像も用意できている */
  | { type: "recorded" }
  | { type: "recordingFailed"; notice: string }
  /** 認識できた */
  | { type: "recognized"; utteranceId: string; text: string }
  /** 認識が空か、失敗した */
  | { type: "recognitionFailed" }
  /** 上限に達していた (409 `limit_reached`)。応答を出さずに、区切る */
  | { type: "limitReached" }
  /** 応答が来た */
  | { type: "replied"; speech: Speech }
  | { type: "replyFailed" }
  /** ペンが離れてから 2 秒たった (書き込み中ではない) */
  | { type: "penReady" }
  /** 字幕と音声が終わった。音声が出せなかったときも、この事象で続ける */
  | { type: "speechEnded" }
  /** 時計が期限に達した */
  | { type: "timeUp" };

/** 相談の最初の状態。先輩先生は最初に話さず、利用者の発話から始める */
export function createConsultationState(): ConsultationState {
  return {
    seq: 0,
    subtitles: [],
    expression: "normal",
    gesture: "nod",
    notice: null,
    timeUp: false,
    shown: null,
    phase: "idle",
  };
}

/** 応答の結果 (`ConsultationReply`) を、状態の発話にする */
export function replySpeech(reply: ConsultationReply): Speech {
  return {
    turnId: reply.turn.turnId,
    text: reply.turn.text,
    expression: reply.turn.expression ?? "normal",
    boardWrites: reply.boardWrites,
    limitReached: reply.limitReached,
  };
}

function sharedOf(state: ConsultationState): Shared {
  return {
    seq: state.seq,
    subtitles: state.subtitles,
    expression: state.expression,
    gesture: state.gesture,
    notice: state.notice,
    timeUp: state.timeUp,
    shown: state.shown,
  };
}

/** フェーズが変わるときの共通の処理。`seq` を進める */
function enter(state: ConsultationState, patch: Partial<Shared> = {}): Shared {
  return { ...sharedOf(state), ...patch, seq: state.seq + 1 };
}

/** 期限に達しているときは区切る。そうでなければ発話待ちへ戻す */
function endTurn(state: ConsultationState, patch: Partial<Shared> = {}): ConsultationState {
  if (state.timeUp) {
    return toEnded(state, patch);
  }
  return { ...enter(state, patch), phase: "idle" };
}

function toEnded(state: ConsultationState, patch: Partial<Shared> = {}): ConsultationState {
  return { ...enter(state, { ...patch, notice: ENDED_MESSAGE }), phase: "ended" };
}

/** 相談の進行の遷移。仕様書 5.4 と architecture 5.2 の規則に従い、合わない事象は無視する */
export function consultationReducer(
  state: ConsultationState,
  event: ConsultationEvent,
): ConsultationState {
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
          gesture: "think",
          notice: null,
        }),
        phase: "awaitingReply",
        utteranceId: event.utteranceId,
      };

    case "recognitionFailed":
      return state.phase === "transcribing"
        ? endTurn(state, { notice: RECOGNITION_FAILED_MESSAGE })
        : state;

    case "limitReached":
      // 録音は捨てて (認識も応答もしない)、区切る
      return state.phase === "transcribing" || state.phase === "awaitingReply"
        ? toEnded(state)
        : state;

    case "replied":
      // 応答が来ても、すぐには出さない。ペンの書き込みが終わるまで待つ (holding)
      if (state.phase !== "awaitingReply") {
        return state;
      }
      return {
        ...enter(state, { expression: "normal", gesture: "nod" }),
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
          subtitles: [...state.subtitles, { speaker: "mentor", text: state.speech.text }],
          expression: state.speech.expression,
          gesture: "talk",
          shown: { boardWrites: state.speech.boardWrites },
        }),
        phase: "speaking",
        speech: state.speech,
      };

    case "speechEnded":
      if (state.phase !== "speaking") {
        return state;
      }
      return state.speech.limitReached ? toEnded(state) : endTurn(state, { gesture: "nod" });

    case "timeUp":
      switch (state.phase) {
        case "idle":
        case "recording":
          // 録音の途中は、録音を捨てて区切る (仕様書 5.4)
          return toEnded(state);
        case "holding":
          // 保留中の応答は、音声なしで字幕だけ出してから区切る (architecture 5.2)
          return toEnded(state, {
            subtitles: [...state.subtitles, { speaker: "mentor", text: state.speech.text }],
            expression: state.speech.expression,
            gesture: "nod",
            shown: { boardWrites: state.speech.boardWrites },
            timeUp: true,
          });
        case "ended":
          return state;
        default:
          // 認識、応答、発話の途中は、終わるまで待ってから区切る
          return state.timeUp ? state : { ...state, timeUp: true };
      }

    default:
      return state;
  }
}
