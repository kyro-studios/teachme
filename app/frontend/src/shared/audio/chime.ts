// チャイムの音 (WebAudio)。短い 2 音を鳴らす。音が出せない端末では、何もしない (進行は止めない)

/** 1 音の長さ (秒) と、音と音の間隔 (秒) */
const NOTE_SECONDS = 0.35;
const NOTE_GAP_SECONDS = 0.4;
/** 高い音から低い音へ。周波数 (Hz) */
const NOTES_HZ = [880, 660];

export function playChime(): void {
  try {
    const context = new AudioContext();
    const start = context.currentTime;
    NOTES_HZ.forEach((frequency, index) => {
      const at = start + index * NOTE_GAP_SECONDS;
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.2, at);
      gain.gain.exponentialRampToValueAtTime(0.001, at + NOTE_SECONDS);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(at);
      oscillator.stop(at + NOTE_SECONDS);
    });
    const closeAfterSeconds = NOTE_GAP_SECONDS * NOTES_HZ.length + 0.5;
    setTimeout(() => void context.close(), closeAfterSeconds * 1000);
  } catch {
    // 音が出せない (AudioContext が無い、再生が許されない) ときは、表示だけで続ける
  }
}
