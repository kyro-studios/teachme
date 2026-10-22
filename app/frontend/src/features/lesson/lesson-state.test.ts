import { describe, expect, test } from "vitest";
import {
  CHIME_MESSAGE,
  createLessonState,
  FILLERS,
  lessonReducer,
  pickFiller,
  RECOGNITION_FAILED_MESSAGE,
  REPLY_FAILED_MESSAGE,
  replySpeech,
  openingSpeech,
  type LessonEvent,
  type LessonState,
  type Speech,
} from "./lesson-state";

const OPENING: Speech = {
  turnId: "turn-opening",
  text: "よろしくお願いします。",
  expression: "normal",
  annotation: null,
  limitReached: false,
};

const REPLY: Speech = {
  turnId: "turn-reply",
  text: "なぜそうなるのですか？",
  expression: "troubled",
  annotation: { kind: "point", region: { x: 0.1, y: 0.2, width: 0.3, height: 0.1 } },
  limitReached: false,
};

const RECOGNIZED = {
  type: "recognized",
  utteranceId: "utterance-1",
  text: "頂点は平方完成で求めます",
  filler: "ふむ…",
} as const;

function run(state: LessonState, ...events: LessonEvent[]): LessonState {
  return events.reduce(lessonReducer, state);
}

/** 授業の開始の発話が終わり、発話待ちになった状態 */
function idle(): LessonState {
  return run(createLessonState(OPENING), { type: "speechEnded" });
}

/** 認識が済み、応答を待っている状態 */
function awaitingReply(): LessonState {
  return run(idle(), { type: "talk" }, { type: "recorded" }, RECOGNIZED);
}

describe("授業の開始", () => {
  test("開始の発話を字幕「生徒」に出し、最初の状態は開始の発話の途中にする", () => {
    const state = createLessonState(OPENING);

    expect(state.phase).toBe("opening");
    expect(state.subtitles).toEqual([{ speaker: "student", text: "よろしくお願いします。" }]);
  });

  test("開始の発話が終わると、発話待ちになる", () => {
    expect(idle().phase).toBe("idle");
  });

  test("開始の結果の発話を、状態の発話に変換する。表情が無ければ通常にする", () => {
    expect(
      openingSpeech({ turnId: "t", speaker: "student", text: "はい", expression: "convinced" }),
    ).toEqual({
      turnId: "t",
      text: "はい",
      expression: "convinced",
      annotation: null,
      limitReached: false,
    });
    expect(openingSpeech({ turnId: "t", speaker: "student", text: "はい" }).expression).toBe(
      "normal",
    );
  });
});

describe("発話と認識", () => {
  test("「話す」で録音を始め、もう一度押しても二重に録音を始めない", () => {
    const recording = run(idle(), { type: "talk" });
    expect(recording.phase).toBe("recording");

    expect(lessonReducer(recording, { type: "talk" })).toBe(recording);
  });

  test("発話待ち以外では、「話す」を受け付けない", () => {
    const transcribing = run(idle(), { type: "talk" }, { type: "recorded" });
    const waiting = awaitingReply();
    const holding = run(waiting, { type: "replied", speech: REPLY });
    const speaking = run(holding, { type: "penReady" });
    const chime = run(idle(), { type: "timeUp" });

    for (const state of [transcribing, waiting, holding, speaking, chime]) {
      expect(lessonReducer(state, { type: "talk" })).toBe(state);
    }
  });

  test("認識できたら、字幕「あなた」を出し、相槌を 1 つ表示して応答を待つ", () => {
    const state = awaitingReply();

    expect(state.phase).toBe("awaitingReply");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "you", text: "頂点は平方完成で求めます" });
    expect(state.filler).toBe("ふむ…");
    expect(state.expression).toBe("thinking");
  });

  test("認識が空か失敗したら、文言を出して発話待ちに戻し、字幕は残さない", () => {
    const state = run(
      idle(),
      { type: "talk" },
      { type: "recorded" },
      { type: "recognitionFailed" },
    );

    expect(state.phase).toBe("idle");
    expect(state.notice).toBe(RECOGNITION_FAILED_MESSAGE);
    expect(state.subtitles).toHaveLength(1);
  });

  test("録音の準備が失敗したら、その文言を出して発話待ちに戻す", () => {
    const state = run(
      idle(),
      { type: "talk" },
      { type: "recordingFailed", notice: "マイクを使えませんでした" },
    );

    expect(state.phase).toBe("idle");
    expect(state.notice).toBe("マイクを使えませんでした");
  });
});

describe("応答と生徒の発話", () => {
  test("応答が来ても、すぐには字幕を出さず、ペンの待ちに入る", () => {
    const state = run(awaitingReply(), { type: "replied", speech: REPLY });

    expect(state.phase).toBe("holding");
    expect(state.expression).toBe("normal");
    expect(state.subtitles.some((subtitle) => subtitle.text === REPLY.text)).toBe(false);
  });

  test("ペンの待ちが済むと、生徒の字幕と印を出して、発話の途中に入る", () => {
    const state = run(awaitingReply(), { type: "replied", speech: REPLY }, { type: "penReady" });

    expect(state.phase).toBe("speaking");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "student", text: REPLY.text });
    expect(state.expression).toBe("troubled");
    expect(state.shown).toEqual({ annotation: REPLY.annotation });
  });

  test("字幕と音声が終わり、上限に達していなければ、発話待ちに戻る", () => {
    const state = run(
      awaitingReply(),
      { type: "replied", speech: REPLY },
      { type: "penReady" },
      { type: "speechEnded" },
    );

    expect(state.phase).toBe("idle");
    expect(state.filler).toBeNull();
  });

  test("生徒の応答が失敗したら、文言を出して発話待ちに戻し、生徒の字幕は出さない", () => {
    const state = run(awaitingReply(), { type: "replyFailed" });

    expect(state.phase).toBe("idle");
    expect(state.notice).toBe(REPLY_FAILED_MESSAGE);
    expect(state.subtitles.map((subtitle) => subtitle.speaker)).toEqual(["student", "you"]);
  });

  test("応答の時点で上限に達していれば、応答を出さずにチャイムへ進む", () => {
    const state = run(awaitingReply(), { type: "limitReached" });

    expect(state.phase).toBe("chime");
    expect(state.notice).toBe(CHIME_MESSAGE);
    expect(state.subtitles).toHaveLength(2);
  });

  test("応答が上限に達していたら、字幕と音声が終わってからチャイムへ進む", () => {
    const last = { ...REPLY, limitReached: true };
    const state = run(
      awaitingReply(),
      { type: "replied", speech: last },
      { type: "penReady" },
      { type: "speechEnded" },
    );

    expect(state.phase).toBe("chime");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "student", text: REPLY.text });
  });

  test("字幕を出すたびに、印は新しい値になる (ボードへ 1 回だけ書くため)", () => {
    const first = run(awaitingReply(), { type: "replied", speech: REPLY }, { type: "penReady" });
    const second = run(
      first,
      { type: "speechEnded" },
      { type: "talk" },
      { type: "recorded" },
      RECOGNIZED,
    );
    const next = run(
      second,
      { type: "replied", speech: { ...REPLY, annotation: null } },
      { type: "penReady" },
    );

    expect(next.shown).not.toBe(first.shown);
    expect(next.shown).toEqual({ annotation: null });
  });
});

describe("状態の番号 (seq)", () => {
  test("状態が変わるたびに増える", () => {
    const before = idle();
    const after = run(before, { type: "talk" });

    expect(after.seq).toBe(before.seq + 1);
  });

  test("期限に達しても、状態が変わらないときは増えない (音声や録音を作り直さないため)", () => {
    const waiting = awaitingReply();

    const next = lessonReducer(waiting, { type: "timeUp" });

    expect(next.phase).toBe("awaitingReply");
    expect(next.seq).toBe(waiting.seq);
  });
});

describe("期限の扱い", () => {
  test("発話待ちで期限に達したら、すぐにチャイムへ", () => {
    const state = run(idle(), { type: "timeUp" });

    expect(state.phase).toBe("chime");
    expect(state.notice).toBe(CHIME_MESSAGE);
  });

  test("録音中に期限に達したら、チャイムへ進む (録音は捨てる)", () => {
    const state = run(idle(), { type: "talk" }, { type: "timeUp" });

    expect(state.phase).toBe("chime");
  });

  test("応答を待つ間に期限に達しても、応答の字幕と音声が終わるまで待つ", () => {
    const state = run(awaitingReply(), { type: "timeUp" });
    expect(state.phase).toBe("awaitingReply");

    const next = run(state, { type: "replied", speech: REPLY }, { type: "penReady" });
    expect(next.phase).toBe("speaking");

    expect(run(next, { type: "speechEnded" }).phase).toBe("chime");
  });

  test("保留中に期限に達したら、応答の字幕を音声なしで出して、すぐにチャイムへ", () => {
    const holding = run(awaitingReply(), { type: "replied", speech: REPLY });

    const state = run(holding, { type: "timeUp" });

    expect(state.phase).toBe("chime");
    expect(state.subtitles.at(-1)).toEqual({ speaker: "student", text: REPLY.text });
    expect(state.shown).toEqual({ annotation: REPLY.annotation });
  });

  test("生徒の発話の途中に期限に達したら、音声が終わってからチャイムへ", () => {
    const speaking = run(
      awaitingReply(),
      { type: "replied", speech: REPLY },
      { type: "penReady" },
      { type: "timeUp" },
    );

    expect(speaking.phase).toBe("speaking");
    expect(run(speaking, { type: "speechEnded" }).phase).toBe("chime");
  });

  test("開始の発話の途中に期限に達したら、発話が終わってからチャイムへ", () => {
    const opening = run(createLessonState(OPENING), { type: "timeUp" });

    expect(opening.phase).toBe("opening");
    expect(run(opening, { type: "speechEnded" }).phase).toBe("chime");
  });

  test("認識の途中に期限に達して、応答が失敗したら、発話待ちではなくチャイムへ", () => {
    const transcribing = run(idle(), { type: "talk" }, { type: "recorded" }, { type: "timeUp" });

    const state = run(transcribing, RECOGNIZED, { type: "replyFailed" });

    expect(state.phase).toBe("chime");
  });

  test("チャイムの後は、どの事象も状態を変えない", () => {
    const chime = run(idle(), { type: "timeUp" });

    expect(lessonReducer(chime, { type: "timeUp" })).toBe(chime);
    expect(lessonReducer(chime, { type: "speechEnded" })).toBe(chime);
  });
});

describe("相槌", () => {
  test("乱数から、相槌を 1 つ選ぶ", () => {
    expect(pickFiller(() => 0)).toBe(FILLERS[0]);
    expect(pickFiller(() => 0.99)).toBe(FILLERS[FILLERS.length - 1]);
  });

  test("応答の結果を、状態の発話に変換する", () => {
    expect(
      replySpeech({
        turn: { turnId: "turn-reply", speaker: "student", text: "はい" },
        annotation: null,
        roundCount: 1,
        limitReached: false,
      }),
    ).toEqual({
      turnId: "turn-reply",
      text: "はい",
      expression: "normal",
      annotation: null,
      limitReached: false,
    });
  });
});
