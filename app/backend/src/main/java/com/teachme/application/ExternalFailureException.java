package com.teachme.application;

/**
 * 外部 AI の失敗で、今の操作を完了できないこと (502)。
 *
 * <p>`code` は機械が見る識別子 (`tts_failed` など)、メッセージは画面に出してよい文言。HTTP の状態への変換は `web` が行う。
 */
public final class ExternalFailureException extends RuntimeException {

  private final String code;

  public ExternalFailureException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
