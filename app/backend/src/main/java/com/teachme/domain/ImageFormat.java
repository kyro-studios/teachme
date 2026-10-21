package com.teachme.domain;

import java.util.Optional;

/** 画像の形式 (仕様書 5.3、5.2)。JPEG か PNG だけを受け付け、1 枚 10 MB 以下とする。 */
public enum ImageFormat {
  JPEG("image/jpeg", new int[] {0xFF, 0xD8, 0xFF}),
  PNG("image/png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

  /** 1 枚の大きさの上限 (バイト)。 */
  public static final long MAX_BYTES = 10L * 1024 * 1024;

  private final String mimeType;
  private final int[] signature;

  ImageFormat(String mimeType, int[] signature) {
    this.mimeType = mimeType;
    this.signature = signature;
  }

  /** 画像の大きさが上限以下か。 */
  public static boolean isWithinLimit(long size) {
    return size >= 0 && size <= MAX_BYTES;
  }

  /** 先頭のバイト列 (ファイルの署名) から形式を求める。どちらでもなければ空。拡張子や申告された種類は見ない。 */
  public static Optional<ImageFormat> detect(byte[] bytes) {
    for (ImageFormat format : values()) {
      if (format.matches(bytes)) {
        return Optional.of(format);
      }
    }
    return Optional.empty();
  }

  public String mimeType() {
    return mimeType;
  }

  private boolean matches(byte[] bytes) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((bytes[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
