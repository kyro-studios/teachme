import { afterEach, beforeEach, expect, test, vi } from "vitest";
import {
  chooseRecordingMimeType,
  MAX_RECORDING_BYTES,
  MAX_RECORDING_MS,
  RecordingError,
  startRecording,
} from "./recorder";

// jsdom には MediaRecorder が無いため、端末の録音を真似る部品を用意する
class FakeMediaRecorder extends EventTarget {
  static instances: FakeMediaRecorder[] = [];
  static supportedTypes = new Set<string>();
  static isTypeSupported = (type: string) => FakeMediaRecorder.supportedTypes.has(type);

  state: "inactive" | "recording" = "inactive";
  mimeType: string;

  constructor(
    readonly stream: MediaStream,
    readonly options?: MediaRecorderOptions,
  ) {
    super();
    this.mimeType = options?.mimeType ?? "audio/webm";
    FakeMediaRecorder.instances.push(this);
  }

  start() {
    this.state = "recording";
  }

  stop() {
    this.state = "inactive";
    this.dispatchEvent(new Event("stop"));
  }

  /** 録音のデータを 1 件渡す (`size` バイト)。端末は、録音中にこの形でデータを渡す */
  emitData(size: number) {
    const event = new Event("dataavailable") as Event & { data: Blob };
    event.data = new Blob([new Uint8Array(size)]);
    this.dispatchEvent(event);
  }
}

const track = { stop: vi.fn() };
const stream = { getTracks: () => [track] } as unknown as MediaStream;
const getUserMedia = vi.fn();

function lastRecorder(): FakeMediaRecorder {
  const recorder = FakeMediaRecorder.instances.at(-1);
  if (!recorder) {
    throw new Error("録音が始まっていません");
  }
  return recorder;
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeMediaRecorder.instances = [];
  FakeMediaRecorder.supportedTypes = new Set(["audio/webm", "audio/mp4"]);
  getUserMedia.mockReset().mockResolvedValue(stream);
  track.stop.mockClear();
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia },
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  delete (navigator as { mediaDevices?: unknown }).mediaDevices;
});

test("fake モードは、マイクを使わず、固定の短い音声を返す", async () => {
  const recording = await startRecording({ aiMode: "fake" });

  recording.stop();

  const result = await recording.finished;
  expect(getUserMedia).not.toHaveBeenCalled();
  expect(FakeMediaRecorder.instances).toHaveLength(0);
  expect(result.endedBy).toBe("stopped");
  expect(result.blob.type).toBe("audio/wav");
  expect(result.blob.size).toBeGreaterThan(0);
});

test("fake モードでも、60 秒で自動で止まる", async () => {
  const recording = await startRecording({ aiMode: "fake" });

  vi.advanceTimersByTime(MAX_RECORDING_MS - 1);
  let settled = false;
  void recording.finished.then(() => (settled = true));
  await Promise.resolve();
  expect(settled).toBe(false);

  vi.advanceTimersByTime(1);
  await expect(recording.finished).resolves.toMatchObject({ endedBy: "time_limit" });
  expect(getUserMedia).not.toHaveBeenCalled();
});

test("形式は、端末が対応する webm を優先し、無ければ mp4 を選ぶ", () => {
  expect(chooseRecordingMimeType((type) => type === "audio/webm")).toBe("audio/webm");
  expect(chooseRecordingMimeType((type) => type === "audio/mp4")).toBe("audio/mp4");
  expect(chooseRecordingMimeType(() => true)).toBe("audio/webm");
  expect(chooseRecordingMimeType(() => false)).toBeUndefined();
});

test("録音の始まりに、選んだ形式を MediaRecorder に渡す", async () => {
  FakeMediaRecorder.supportedTypes = new Set(["audio/mp4"]);

  await startRecording({ aiMode: "real" });

  expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
  expect(lastRecorder().options?.mimeType).toBe("audio/mp4");
});

test("実際の録音も 60 秒で止まり、録れた音声と理由 (time_limit) を返し、マイクを解放する", async () => {
  const recording = await startRecording({ aiMode: "real" });
  const recorder = lastRecorder();
  recorder.emitData(1_000);

  vi.advanceTimersByTime(MAX_RECORDING_MS);

  const result = await recording.finished;
  expect(recorder.state).toBe("inactive");
  expect(result.endedBy).toBe("time_limit");
  expect(result.blob.size).toBe(1_000);
  expect(track.stop).toHaveBeenCalled();
});

test("利用者が途中で止めたときは、止めた理由 (stopped) で返し、60 秒の計時を止める", async () => {
  const recording = await startRecording({ aiMode: "real" });
  lastRecorder().emitData(500);

  recording.stop();
  await expect(recording.finished).resolves.toMatchObject({ endedBy: "stopped" });

  // 止めた後に 60 秒を過ぎても、二重に止めない
  const recorder = lastRecorder();
  const stopSpy = vi.spyOn(recorder, "stop");
  vi.advanceTimersByTime(MAX_RECORDING_MS);
  expect(stopSpy).not.toHaveBeenCalled();
});

test("録音が 5 MB に達したら止まり、上限を超えた分は音声に含めない", async () => {
  const recording = await startRecording({ aiMode: "real" });
  const recorder = lastRecorder();
  recorder.emitData(MAX_RECORDING_BYTES - 10);

  recorder.emitData(100);

  const result = await recording.finished;
  expect(result.endedBy).toBe("size_limit");
  expect(result.blob.size).toBe(MAX_RECORDING_BYTES - 10);
});

test("マイクの許可が無いときは、microphone_denied の RecordingError を投げる", async () => {
  getUserMedia.mockRejectedValue(new DOMException("denied", "NotAllowedError"));

  const error = await startRecording({ aiMode: "real" }).catch((e: unknown) => e);

  expect(error).toBeInstanceOf(RecordingError);
  expect((error as RecordingError).code).toBe("microphone_denied");
});

test("マイクが使えないその他の失敗は、microphone_unavailable の RecordingError を投げる", async () => {
  getUserMedia.mockRejectedValue(new DOMException("none", "NotFoundError"));

  const error = await startRecording({ aiMode: "real" }).catch((e: unknown) => e);

  expect((error as RecordingError).code).toBe("microphone_unavailable");
});

test("MediaRecorder の作成に失敗したら、マイクを解放し、microphone_unavailable の RecordingError を投げる", async () => {
  vi.stubGlobal(
    "MediaRecorder",
    class extends FakeMediaRecorder {
      constructor(stream: MediaStream, options?: MediaRecorderOptions) {
        super(stream, options);
        throw new DOMException("not supported", "NotSupportedError");
      }
    },
  );

  const error = await startRecording({ aiMode: "real" }).catch((e: unknown) => e);

  expect(error).toBeInstanceOf(RecordingError);
  expect((error as RecordingError).code).toBe("microphone_unavailable");
  expect(track.stop).toHaveBeenCalled();
});

test("録音の開始 (start) に失敗したら、マイクを解放し、microphone_unavailable の RecordingError を投げる", async () => {
  vi.stubGlobal(
    "MediaRecorder",
    class extends FakeMediaRecorder {
      start(): void {
        throw new DOMException("cannot start", "InvalidStateError");
      }
    },
  );

  const error = await startRecording({ aiMode: "real" }).catch((e: unknown) => e);

  expect(error).toBeInstanceOf(RecordingError);
  expect((error as RecordingError).code).toBe("microphone_unavailable");
  expect(track.stop).toHaveBeenCalled();
  // 失敗した後は、60 秒の計時も残らない
  expect(vi.getTimerCount()).toBe(0);
});
