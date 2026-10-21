package com.teachme.application;

/**
 * 対象が無いこと (404)。ほかの利用者のものも、無いものとして扱う。
 *
 * <p>HTTP の状態への変換は `web` が行う。
 */
public final class NotFoundException extends RuntimeException {

  public NotFoundException() {
    super("対象が見つかりません");
  }
}
