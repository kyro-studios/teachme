// 録音 (MediaRecorder)。最大 60 秒と 5 MB まで (NFR-201)。aiMode が fake のときは、マイクを使わず、固定の短い音声を返す

/** AI の動作モード (`GET /config` の aiMode)。shared は api に依存しないため、ここでも定める */
export type AiMode = "real" | "fake";

/** 録音の上限 (ミリ秒)。仕様書 5.5 と NFR-201 */
export const MAX_RECORDING_MS = 60_000;

/** 録音の上限 (バイト)。API の録音は 5 MB 以下 */
export const MAX_RECORDING_BYTES = 5 * 1024 * 1024;

/** 録音の形式の候補。先に書いたものを優先する */
const MIME_TYPE_CANDIDATES = ["audio/webm", "audio/mp4"];

/** 録音の音質。音声の認識に十分で、60 秒でも上限の 5 MB に届かない大きさにする */
const AUDIO_BITS_PER_SECOND = 64_000;

/** データを受け取る間隔 (ミリ秒)。途中のデータで、上限の検査を行うため */
const CHUNK_INTERVAL_MS = 1_000;

/** 録音が終わった理由。上限に達したときは、画面が続きの処理 (認識) へ進む */
export type RecordingEnd = "stopped" | "time_limit" | "size_limit";

export type RecordingErrorCode = "microphone_denied" | "microphone_unavailable" | "unsupported";

/** 録音の失敗。`code` で、画面が出す文言と取る行動を決める */
export class RecordingError extends Error {
  readonly code: RecordingErrorCode;

  constructor(code: RecordingErrorCode, message: string) {
    super(message);
    this.name = "RecordingError";
    this.code = code;
  }
}

export type FinishedRecording = {
  blob: Blob;
  endedBy: RecordingEnd;
};

export type Recording = {
  /** 録音を終える。終わった後に呼んでも何もしない */
  stop: () => void;
  /** 録音が終わったときに解決する。上限に達したときも、ここで知らせる */
  finished: Promise<FinishedRecording>;
};

/** 録音の方式ごとの部品。上限の検査は、startRecording が共通に行う */
type Capture = {
  stop: (endedBy: RecordingEnd) => void;
  done: Promise<FinishedRecording>;
};

/**
 * 端末が対応する録音の形式を、候補の順に選ぶ。どれも対応しなければ undefined を返し、端末の既定に任せる。
 * isSupported は、MediaRecorder.isTypeSupported を渡す
 */
export function chooseRecordingMimeType(
  isSupported: (mimeType: string) => boolean,
): string | undefined {
  return MIME_TYPE_CANDIDATES.find((mimeType) => isSupported(mimeType));
}

/**
 * 録音を始める。マイクの許可が無いときなど、始められないときは RecordingError を投げる。
 * 60 秒か 5 MB に達したら、自動で止まり、`finished` で知らせる
 */
export async function startRecording({ aiMode }: { aiMode: AiMode }): Promise<Recording> {
  const capture = aiMode === "fake" ? startFakeCapture() : await startMicrophoneCapture();

  const timer = setTimeout(() => capture.stop("time_limit"), MAX_RECORDING_MS);
  const clearTimer = () => clearTimeout(timer);
  capture.done.then(clearTimer, clearTimer);

  return {
    stop: () => capture.stop("stopped"),
    finished: capture.done,
  };
}

async function startMicrophoneCapture(): Promise<Capture> {
  const stream = await openMicrophone();
  try {
    return beginMicrophoneCapture(stream);
  } catch {
    // 録音の初期化の途中で失敗しても、取得済みのマイクは必ず解放する
    releaseMicrophone(stream);
    throw new RecordingError(
      "microphone_unavailable",
      "録音を始められませんでした。もう一度試してください",
    );
  }
}

/** 取得済みのマイクで録音を始める。失敗したら例外を投げ、呼び出し元がマイクを解放する */
function beginMicrophoneCapture(stream: MediaStream): Capture {
  const mimeType = chooseRecordingMimeType((type) => MediaRecorder.isTypeSupported(type));

  const recorder = new MediaRecorder(stream, {
    mimeType,
    audioBitsPerSecond: AUDIO_BITS_PER_SECOND,
  });
  const chunks: Blob[] = [];
  let bytes = 0;
  let endedBy: RecordingEnd = "stopped";

  const stop = (reason: RecordingEnd) => {
    // 止めた理由は、最初に止めた側が決める (上限と、利用者の操作が同時のときも同じ)
    if (recorder.state === "inactive") {
      return;
    }
    endedBy = reason;
    recorder.stop();
  };

  const done = new Promise<FinishedRecording>((resolve, reject) => {
    recorder.addEventListener("dataavailable", (event) => {
      if (event.data.size === 0) {
        return;
      }
      // 上限を超えるデータは音声に入れず、ここで録音を止める
      if (bytes + event.data.size > MAX_RECORDING_BYTES) {
        stop("size_limit");
        return;
      }
      chunks.push(event.data);
      bytes += event.data.size;
    });
    recorder.addEventListener("stop", () => {
      releaseMicrophone(stream);
      resolve({ blob: new Blob(chunks, { type: recorder.mimeType || mimeType }), endedBy });
    });
    recorder.addEventListener("error", () => {
      releaseMicrophone(stream);
      reject(
        new RecordingError(
          "microphone_unavailable",
          "録音できませんでした。もう一度試してください",
        ),
      );
    });
  });

  recorder.start(CHUNK_INTERVAL_MS);
  return { stop, done };
}

/** aiMode が fake のときの録音。マイクには触れず、固定の短い音声を返す */
function startFakeCapture(): Capture {
  let finish!: (result: FinishedRecording) => void;
  const done = new Promise<FinishedRecording>((resolve) => {
    finish = resolve;
  });
  return {
    stop: (endedBy) => finish({ blob: createFakeClip(), endedBy }),
    done,
  };
}

async function openMicrophone(): Promise<MediaStream> {
  if (typeof MediaRecorder === "undefined" || !navigator.mediaDevices?.getUserMedia) {
    throw new RecordingError("unsupported", "この端末では録音できません");
  }
  try {
    return await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (error) {
    // 許可が無いときは、ブラウザが NotAllowedError を投げる。安全な接続でないときは SecurityError
    const name = (error as { name?: string } | null)?.name;
    if (name === "NotAllowedError" || name === "SecurityError") {
      throw new RecordingError(
        "microphone_denied",
        "マイクの使用が許可されていません。ブラウザの設定で許可してください",
      );
    }
    throw new RecordingError("microphone_unavailable", "マイクを使えませんでした");
  }
}

function releaseMicrophone(stream: MediaStream) {
  for (const track of stream.getTracks()) {
    track.stop();
  }
}

/** 固定の短い音声。無音の WAV (8 kHz、16 ビット、モノラル、0.2 秒)。フェイクの音声認識は中身を見ない */
function createFakeClip(): Blob {
  const sampleRate = 8_000;
  const dataSize = (sampleRate / 5) * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);
  const writeText = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) {
      view.setUint8(offset + i, text.charCodeAt(i));
    }
  };

  writeText(0, "RIFF");
  view.setUint32(4, 36 + dataSize, true);
  writeText(8, "WAVE");
  writeText(12, "fmt ");
  view.setUint32(16, 16, true); // fmt チャンクの長さ (PCM)
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 1, true); // モノラル
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); // 1 秒あたりのバイト数
  view.setUint16(32, 2, true); // 1 サンプルのバイト数
  view.setUint16(34, 16, true); // ビット数
  writeText(36, "data");
  view.setUint32(40, dataSize, true);
  // 残りは 0 のままにするため、無音になる

  return new Blob([buffer], { type: "audio/wav" });
}
