package com.teachme.ai;

/** 音声認識 (STT)。認識結果は空の文字列もありうる (無言や雑音のみ)。失敗は {@link AiFailedException} で表す。 */
public interface SpeechToText {

  /**
   * @param recording 録音
   * @return 認識結果の文章
   */
  String transcribe(AiCallContext context, Media recording);
}
