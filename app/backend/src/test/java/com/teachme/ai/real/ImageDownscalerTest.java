package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.teachme.ai.Media;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageDownscalerTest {

  private static Media image(String format, String mimeType, int width, int height) {
    try {
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return new Media(mimeType, out.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BufferedImage decode(Media media) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(media.bytes()));
  }

  @Test
  void 上限以下の画像は_そのまま返す() {
    Media small = image("png", "image/png", 1280, 720);

    assertSame(small, ImageDownscaler.limit(small));
  }

  @Test
  void 長辺が上限を超える_PNG_は_縮めて_PNG_のまま返す() throws IOException {
    Media large = image("png", "image/png", 3000, 1500);

    Media limited = ImageDownscaler.limit(large);

    assertEquals("image/png", limited.mimeType());
    BufferedImage decoded = decode(limited);
    assertEquals(1280, decoded.getWidth());
    assertEquals(640, decoded.getHeight());
  }

  @Test
  void 縦長の_JPEG_は_長辺を_1280_にして_JPEG_のまま返す() throws IOException {
    Media large = image("jpeg", "image/jpeg", 1000, 2560);

    Media limited = ImageDownscaler.limit(large);

    assertEquals("image/jpeg", limited.mimeType());
    BufferedImage decoded = decode(limited);
    assertEquals(500, decoded.getWidth());
    assertEquals(1280, decoded.getHeight());
  }

  @Test
  void 対応しない形式と_読めない中身は_そのまま返す() {
    Media unknownType = new Media("image/gif", new byte[] {1, 2, 3});
    Media broken = new Media("image/png", new byte[] {1, 2, 3});

    assertSame(unknownType, ImageDownscaler.limit(unknownType));
    assertSame(broken, ImageDownscaler.limit(broken));
    assertArrayEquals(new byte[] {1, 2, 3}, ImageDownscaler.limit(broken).bytes());
  }
}
