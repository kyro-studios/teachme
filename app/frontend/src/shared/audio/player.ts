// 再生 (HTMLAudioElement)。開始、終了、失敗をコールバックで知らせる

export type PlaybackHandlers = {
  /** 音声の再生が始まったとき */
  onStart?: () => void;
  /** 最後まで再生し終えたとき */
  onEnd?: () => void;
  /** 再生できなかったとき (音声の取得の失敗、自動再生の拒否など)。字幕だけで続ける */
  onError?: () => void;
};

export type Playback = {
  /** 再生を止める。止めたときは、コールバックを呼ばない */
  stop: () => void;
};

/** `src` の音声を再生する。すでに終わった再生や、止めた再生には何もしない */
export function playAudio(src: string, handlers: PlaybackHandlers): Playback {
  const audio = new Audio(src);
  const controller = new AbortController();
  const { signal } = controller;

  // 1 回の再生の終わりは、終わり、失敗、停止のどれか 1 つだけにする
  const settle = (callback?: () => void) => {
    if (signal.aborted) {
      return;
    }
    controller.abort();
    audio.pause();
    callback?.();
  };

  audio.addEventListener("playing", () => handlers.onStart?.(), { signal });
  audio.addEventListener("ended", () => settle(handlers.onEnd), { signal });
  audio.addEventListener("error", () => settle(handlers.onError), { signal });

  // play() は、端末によって Promise を返さないことがあるため、Promise にそろえて失敗を拾う
  Promise.resolve()
    .then(() => {
      if (!signal.aborted) {
        return audio.play();
      }
    })
    .catch(() => settle(handlers.onError));

  return {
    stop: () => settle(),
  };
}
