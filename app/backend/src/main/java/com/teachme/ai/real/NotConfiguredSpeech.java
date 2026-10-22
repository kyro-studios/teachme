package com.teachme.ai.real;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.Media;
import com.teachme.ai.SpeechToText;
import com.teachme.ai.TextToSpeech;

/**
 * 実際の音声認識と音声合成の実装が入るまでの代わり (`AI_MODE=real` のとき)。どの呼び出しもネットワークへ出ず、失敗として返す。
 *
 * <p>認識の失敗は「うまく聞き取れませんでした」の表示になり、音声の失敗は字幕だけで続く (仕様書 8 節)。実装が入ったら、置き換える。
 */
public final class NotConfiguredSpeech implements SpeechToText, TextToSpeech {

  @Override
  public String transcribe(AiCallContext context, Media recording) {
    throw new AiFailedException(AiCallKind.STT);
  }

  @Override
  public Media synthesize(AiCallContext context, Voice voice, String text) {
    throw new AiFailedException(AiCallKind.TTS);
  }
}
