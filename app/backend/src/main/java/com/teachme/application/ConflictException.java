package com.teachme.application;

/**
 * 今の状態では、その操作ができないこと (409)。`code` は機械が見る識別子、メッセージは画面に出してよい文言。
 *
 * <p>HTTP の状態への変換は `web` が行う。
 */
public final class ConflictException extends RuntimeException {

  private final String code;

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
