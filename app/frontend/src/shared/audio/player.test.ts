import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { playAudio } from "./player";

// jsdom は音声を再生できないため、play と pause を置き換える。作られた要素は、Audio の代わりで受け取る
const RealAudio = globalThis.Audio;
const play = vi.fn<() => Promise<void>>(() => Promise.resolve());
let element: HTMLAudioElement | undefined;

/** 非同期の処理 (play の Promise や、イベントの後の処理) を終わらせる */
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  play.mockClear();
  vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => play());
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
  vi.stubGlobal("Audio", function (src: string) {
    element = new RealAudio(src);
    return element;
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  element = undefined;
});

test("再生が始まったら onStart、最後まで再生したら onEnd を呼ぶ", async () => {
  const onStart = vi.fn();
  const onEnd = vi.fn();
  playAudio("/api/lessons/1/turns/2/audio", { onStart, onEnd });
  await flush();

  expect(play).toHaveBeenCalledTimes(1);
  element?.dispatchEvent(new Event("playing"));
  expect(onStart).toHaveBeenCalledTimes(1);

  element?.dispatchEvent(new Event("ended"));
  expect(onEnd).toHaveBeenCalledTimes(1);
  expect(element?.src).toContain("/api/lessons/1/turns/2/audio");
});

test("再生を拒否されたら onError を呼ぶ (自動再生の拒否など)", async () => {
  play.mockImplementationOnce(() => Promise.reject(new DOMException("blocked", "NotAllowedError")));
  const onError = vi.fn();
  const onEnd = vi.fn();
  playAudio("/api/x.mp3", { onError, onEnd });
  await flush();

  expect(onError).toHaveBeenCalledTimes(1);
  expect(onEnd).not.toHaveBeenCalled();
});

test("音声の取得に失敗したら (error イベント) onError を呼ぶ", async () => {
  const onError = vi.fn();
  playAudio("/api/x.mp3", { onError });
  await flush();

  element?.dispatchEvent(new Event("error"));
  expect(onError).toHaveBeenCalledTimes(1);
});

test("止めたら再生を止め、そのあとはコールバックを呼ばない", async () => {
  const onStart = vi.fn();
  const onEnd = vi.fn();
  const onError = vi.fn();
  const playback = playAudio("/api/x.mp3", { onStart, onEnd, onError });
  await flush();

  playback.stop();
  element?.dispatchEvent(new Event("playing"));
  element?.dispatchEvent(new Event("ended"));
  element?.dispatchEvent(new Event("error"));

  expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled();
  expect(onStart).not.toHaveBeenCalled();
  expect(onEnd).not.toHaveBeenCalled();
  expect(onError).not.toHaveBeenCalled();
});
