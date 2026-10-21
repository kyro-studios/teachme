package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

  @Test
  void 先頭のバイト列から_JPEG_と_PNG_を判定する() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

    assertEquals(ImageFormat.JPEG, ImageFormat.detect(jpeg).orElseThrow());
    assertEquals(ImageFormat.PNG, ImageFormat.detect(png).orElseThrow());
    assertEquals("image/png", ImageFormat.PNG.mimeType());
  }

  @Test
  void 画像ではない中身や_短すぎる中身は判定しない() {
    assertTrue(ImageFormat.detect("hello".getBytes(StandardCharsets.UTF_8)).isEmpty());
    assertTrue(ImageFormat.detect(new byte[0]).isEmpty());
    assertTrue(ImageFormat.detect(new byte[] {(byte) 0x89, 'P'}).isEmpty());
  }

  @Test
  void 大きさの上限は_10_MB_以下() {
    assertTrue(ImageFormat.isWithinLimit(ImageFormat.MAX_BYTES));
    assertFalse(ImageFormat.isWithinLimit(ImageFormat.MAX_BYTES + 1));
  }
}
