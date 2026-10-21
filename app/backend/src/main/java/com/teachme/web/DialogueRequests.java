package com.teachme.web;

import com.teachme.application.DialogueService.Recording;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 授業と相談で共通の、録音 (`POST /lessons/{id}/utterances`、`POST /consultations/{id}/utterances`) の受け取りと検査。
 *
 * <p>60 秒の上限は、フロントエンドが録音を止めることで守る。ここでは、大きさ (5 MB 以下) と空でないことを検査する。
 */
final class DialogueRequests {

  static final String AUDIO_FIELD = "audio";
  static final String INVALID_AUDIO_CODE = "invalid_audio";
  static final String INVALID_AUDIO_MESSAGE = "録音を送り直してください";

  /** 録音の形式が申告されなかったときの形式。 */
  private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

  private DialogueRequests() {}

  /**
   * 録音を読む。大きさの上限を超えた分は読まない。
   *
   * @throws ApiException 録音が無い、空、または 5 MB を超えるとき (`invalid_audio`)
   */
  static Recording readRecording(Context ctx) {
    UploadedFile file = ctx.uploadedFile(AUDIO_FIELD);
    if (file == null) {
      throw invalidAudio();
    }
    byte[] bytes = readAtMost(file, Recording.MAX_BYTES);
    if (bytes.length == 0 || bytes.length > Recording.MAX_BYTES) {
      throw invalidAudio();
    }
    String mimeType = file.contentType() == null ? UNKNOWN_MIME_TYPE : file.contentType();
    return new Recording(mimeType, bytes);
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
