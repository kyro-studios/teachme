package com.teachme.ai.real;

import com.teachme.ai.Media;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * 画像を送る前に、長辺を上限以下に縮める (アーキテクチャ設計 8 節の外部 AI の費用)。
 *
 * <p>上限以下の画像と、読めない画像は、そのまま返す。形式は、入力と同じ JPEG か PNG に保つ。
 */
final class ImageDownscaler {

  /** 長辺の上限 (px)。 */
  static final int MAX_EDGE = 1280;

  private ImageDownscaler() {}

  static Media limit(Media image) {
    String format = formatOf(image.mimeType());
    if (format == null) {
      return image;
    }
    BufferedImage source;
    try {
      source = ImageIO.read(new ByteArrayInputStream(image.bytes()));
    } catch (IOException e) {
      return image;
    }
    if (source == null) {
      return image;
    }
    int longest = Math.max(source.getWidth(), source.getHeight());
    if (longest <= MAX_EDGE) {
      return image;
    }
    double scale = (double) MAX_EDGE / longest;
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    int type = "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage target = new BufferedImage(width, height, type);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(target, format, out)) {
        return image;
      }
      return new Media(image.mimeType(), out.toByteArray());
    } catch (IOException e) {
      return image;
    }
  }

  /** 対応する ImageIO の形式名。JPEG と PNG 以外は null。 */
  private static String formatOf(String mimeType) {
    return switch (mimeType.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> "jpeg";
      case "image/png" -> "png";
      default -> null;
    };
  }
}
