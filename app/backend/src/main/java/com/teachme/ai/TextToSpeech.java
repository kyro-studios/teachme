package com.teachme.ai;

import com.teachme.domain.StudentType;

/** 音声合成 (TTS)。失敗は {@link AiFailedException} で表す。 */
public interface TextToSpeech {

  /**
   * @param voice 話し手 (AI 生徒の種類か、先輩先生)
   * @param text 読み上げる文章
   * @return 音声 (mp3)
   */
  Media synthesize(AiCallContext context, Voice voice, String text);

  /** 話し手。AI 生徒は種類ごとに、先輩先生は 1 つの声を使う。 */
  sealed interface Voice permits Voice.Student, Voice.Mentor {

    /** AI 生徒の声。 */
    record Student(StudentType studentType) implements Voice {}

    /** 先輩先生の声。 */
    record Mentor() implements Voice {}
  }
}
