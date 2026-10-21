package com.teachme.web;

import com.teachme.application.DialogueService.Recording;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;

/**
 * 授業と相談で共通の、録音 (`POST /lessons/{id}/utterances`、`POST /consultations/{id}/utterances`) の受け取りと検査。
 *
 * <p>60 秒の上限は、フロントエンドが録音を止めることで守る。ここでは、形式 (webm、mp4、wav のいずれか)、大きさ (5 MB 以下)、空でないことを検査する。
 */
final class DialogueRequests {

  static final String AUDIO_FIELD = "audio";
  static final String INVALID_AUDIO_CODE = "invalid_audio";
  static final String INVALID_AUDIO_MESSAGE = "録音を送り直してください";

  /** API 契約の録音の形式 (webm、mp4、wav)。`;codecs=opus` などの付属の部分は除いて比べる。 */
  private static final Set<String> ACCEPTED_MIME_TYPES =
      Set.of(
          "audio/webm",
          "video/webm",
          "audio/mp4",
          "video/mp4",
          "audio/wav",
          "audio/x-wav",
          "audio/wave");

  private DialogueRequests() {}

  /**
   * 録音を読む。形式を先に検査し、対象外なら中身を読まずに拒否する。大きさの上限を超えた分は読まない。
   *
   * @throws ApiException 録音が無い、形式が対象外、空、または 5 MB を超えるとき (`invalid_audio`)
   */
  static Recording readRecording(Context ctx) {
    UploadedFile file = ctx.uploadedFile(AUDIO_FIELD);
    if (file == null) {
      throw invalidAudio();
    }
    String mimeType = mimeTypeOf(file);
    if (mimeType == null) {
      throw invalidAudio();
    }
    byte[] bytes = readAtMost(file, Recording.MAX_BYTES);
    if (bytes.length == 0 || bytes.length > Recording.MAX_BYTES) {
      throw invalidAudio();
    }
    return new Recording(mimeType, bytes);
  }

  /** 申告された形式が受け付ける形式なら、その本体 (小文字、付属の部分を除く) を返す。対象外や無ければ null。 */
  private static String mimeTypeOf(UploadedFile file) {
    String contentType = file.contentType();
    if (contentType == null) {
      return null;
    }
    String mimeType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    return ACCEPTED_MIME_TYPES.contains(mimeType) ? mimeType : null;
  }

  /** 上限より 1 バイト多く読み、超えたかどうかを判定できるようにする。 */
  private static byte[] readAtMost(UploadedFile file, long maxBytes) {
    try (InputStream content = file.content()) {
      return content.readNBytes((int) maxBytes + 1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ApiException invalidAudio() {
    return ApiException.badRequest(INVALID_AUDIO_CODE, INVALID_AUDIO_MESSAGE);
  }
}
