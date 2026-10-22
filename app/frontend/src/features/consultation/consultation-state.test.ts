import { describe, expect, test } from "vitest";
import {
  consultationReducer,
  createConsultationState,
  ENDED_MESSAGE,
  RECOGNITION_FAILED_MESSAGE,
  REPLY_FAILED_MESSAGE,
  type ConsultationEvent,
  type ConsultationState,
  type Speech,
} from "./consultation-state";

const REPLY: Speech = {
  turnId: "turn-reply",
  text: "まず平方完成の形を作ってみましょう。",
  expression: "normal",
  boardWrites: [{ kind: "text", text: "(x - 2)^2 - 1" }],
  limitReached: false,
};

const LAST_REPLY: Speech = { ...REPLY, turnId: "turn-last", limitReached: true };

const RECOGNIZED = {
  type: "recognized",
  utteranceId: "utterance-1",
  text: "ここの式変形が分かりません",
} as const;

function run(state: ConsultationState, ...events: ConsultationEvent[]): ConsultationState {
  return events.reduce(consultationReducer, state);
}

/** 相談を始めた直後の、発話待ちの状態 */
function idle(): ConsultationState {
  return createConsultationState();
}

/** 認識が済み、応答を待っている状態 */
function awaitingReply(): ConsultationState {
  return run(idle(), { type: "talk" }, { type: "recorded" }, RECOGNIZED);
}

/** 応答が来て、字幕と音声を出している状態 (ペンは使っていない) */
function speaking(speech: Speech = REPLY): ConsultationState {
  return run(awaitingReply(), { type: "replied", speech }, { type: "penReady" });
}

describe("相談の開始", () => {
  test("先輩先生は最初に話さず、字幕は空で、発話待ちから始める", () => {
    const state = createConsultationState();

    expect(state.phase).toBe("idle");
    expect(state.subtitles).toEqual([]);
    expect(state.notice).toBeNull();
  });
});

describe("利用者の発話", () => {
  test("「話す」で録音を始め、「話し終える」で認識へ進む", () => {
    const recording = run(idle(), { type: "talk" });
    expect(recording.phase).toBe("recording");

    expect(run(recording, { type: "recorded" }).phase).toBe("transcribing");
  });

  test("認識できると、字幕「あなた」に認識結果を出し、応答を待つ", () => {
    const state = awaitingReply();

    expect(state.phase).toBe("awaitingReply");
    expect(state.subtitles).toEqual([{ speaker: "you", text: "ここの式変形が分かりません" }]);
  });

  test("認識が空か失敗したときは、文言を出して発話待ちへ戻る。往復にも数えない", () => {
    const state = run(
      idle(),
      { type: "talk" },
      { type: "recorded" },
      { type: "recognitionFailed" },
    );

    expect(state.phase).toBe("idle");
    expect(state.notice).toBe(RECOGNITION_FAILED_MESSAGE);
    expect(state.subtitles).toEqual([]);
  });

  test("録音中と認識中は「話す」を受け付けない (二重の呼び出しを防ぐ)", () => {
    const recording = run(idle(), { type: "talk" });
    expect(run(recording, { type: "talk" })).toBe(recording);

    const transcribing = run(recording, { type: "recorded" });
    expect(run(transcribing, { type: "talk" })).toBe(transcribing);
  });
});

describe("先輩先生の応答", () => {
  test("応答が来ても、すぐには字幕を出さない (ペンの待ち)", () => {
    const state = run(awaitingReply(), { type: "replied", speech: REPLY });

    expect(state.phase).toBe("holding");
    expect(state.subtitles).toEqual([{ speaker: "you", text: "ここの式変形が分かりません" }]);
    expect(state.shown).toBeNull();
  });

  test("ペンの待ちが終わると、字幕「先輩先生」を出し、ボードへの書き込みを渡す", () => {
    const state = speaking();

    expect(state.phase).toBe("speaking");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "mentor", text: REPLY.text });
    expect(state.shown).toEqual({ boardWrites: REPLY.boardWrites });
  });

  test("字幕と音声が終わると、発話待ちへ戻る (上限に達していないとき)", () => {
    const state = run(speaking(), { type: "speechEnded" });

    expect(state.phase).toBe("idle");
    expect(state.notice).toBeNull();
  });

  test("応答の作成が失敗したときは、文言を出して「話す」を押せる状態へ戻る。字幕は残す", () => {
    const state = run(awaitingReply(), { type: "replyFailed" });

    expect(state.phase).toBe("idle");
    expect(state.notice).toBe(REPLY_FAILED_MESSAGE);
    expect(state.subtitles).toEqual([{ speaker: "you", text: "ここの式変形が分かりません" }]);
  });
});

describe("相談の上限", () => {
  test("応答が上限に達したときは、字幕と音声が終わってから区切る", () => {
    const holding = run(awaitingReply(), { type: "replied", speech: LAST_REPLY });
    expect(holding.phase).toBe("holding");

    const speakingLast = run(holding, { type: "penReady" });
    expect(speakingLast.phase).toBe("speaking");
    expect(speakingLast.notice).toBeNull();

    const ended = run(speakingLast, { type: "speechEnded" });
    expect(ended.phase).toBe("ended");
    expect(ended.notice).toBe(ENDED_MESSAGE);
  });

  test("区切ったあとは、「話す」を受け付けない", () => {
    const ended = run(speaking(LAST_REPLY), { type: "speechEnded" });

    expect(run(ended, { type: "talk" })).toBe(ended);
  });

  test("応答の受け付けで上限に達していたら (409)、応答を出さずに区切る", () => {
    const state = run(awaitingReply(), { type: "limitReached" });

    expect(state.phase).toBe("ended");
    expect(state.notice).toBe(ENDED_MESSAGE);
    expect(state.subtitles).toEqual([{ speaker: "you", text: "ここの式変形が分かりません" }]);
  });

  test("認識の受け付けで上限に達していたら、録音を捨てて区切る", () => {
    const state = run(idle(), { type: "talk" }, { type: "recorded" }, { type: "limitReached" });

    expect(state.phase).toBe("ended");
    expect(state.subtitles).toEqual([]);
  });
});

describe("期限", () => {
  test("発話待ちで期限に達したら、すぐに区切る", () => {
    const state = run(idle(), { type: "timeUp" });

    expect(state.phase).toBe("ended");
    expect(state.notice).toBe(ENDED_MESSAGE);
  });

  test("録音の途中で期限に達したら、録音を捨てて区切る", () => {
    const state = run(idle(), { type: "talk" }, { type: "timeUp" });

    expect(state.phase).toBe("ended");
    expect(state.subtitles).toEqual([]);
  });

  test("保留中の応答は、音声なしで字幕だけ出してから区切る", () => {
    const state = run(awaitingReply(), { type: "replied", speech: REPLY }, { type: "timeUp" });

    expect(state.phase).toBe("ended");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "mentor", text: REPLY.text });
    expect(state.shown).toEqual({ boardWrites: REPLY.boardWrites });
  });

  test("応答を作っている途中で期限に達したら、応答が来た時点で字幕だけ出して区切る", () => {
    const waiting = run(awaitingReply(), { type: "timeUp" });
    expect(waiting.phase).toBe("awaitingReply");

    const replied = run(waiting, { type: "replied", speech: REPLY });
    expect(replied.phase).toBe("ended");
    expect(replied.notice).toBe(ENDED_MESSAGE);
    expect(replied.subtitles.at(-1)).toEqual({ speaker: "mentor", text: REPLY.text });
    expect(replied.shown).toEqual({ boardWrites: REPLY.boardWrites });
  });

  test("期限の後に応答が来ても、ペンが離れるのを待たず、ペンの書き込みが続いても区切りが止まらない", () => {
    const ended = run(
      awaitingReply(),
      { type: "timeUp" },
      { type: "replied", speech: REPLY },
      { type: "penReady" },
      { type: "speechEnded" },
    );

    expect(ended.phase).toBe("ended");
    expect(ended.notice).toBe(ENDED_MESSAGE);
    expect(ended.subtitles.filter((subtitle) => subtitle.speaker === "mentor")).toHaveLength(1);
  });

  test("先輩先生が話している途中で期限に達したら、字幕と音声が終わってから区切る", () => {
    const timed = run(speaking(), { type: "timeUp" });
    expect(timed.phase).toBe("speaking");

    expect(run(timed, { type: "speechEnded" }).phase).toBe("ended");
  });
});
