package com.teachme.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.teachme.application.DialogueService.Recording;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.OrganizedProblem;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 授業と相談で共通の、録音 (`POST /lessons/{id}/utterances`、`POST /consultations/{id}/utterances`) の受け取りと検査。
 * 応答の API (`reply`) の、ボードの画像と指している範囲の受け取りと検査も持つ。
 *
 * <p>60 秒の上限は、フロントエンドが録音を止めることで守る。ここでは、形式 (webm、mp4、wav のいずれか)、大きさ (5 MB 以下)、空でないことを検査する。
 */
final class DialogueRequests {

  static final String AUDIO_FIELD = "audio";
  static final String INVALID_AUDIO_CODE = "invalid_audio";
  static final String INVALID_AUDIO_MESSAGE = "録音を送り直してください";

  static final String BOARD_IMAGE_FIELD = "boardImage";
  static final String INVALID_BOARD_IMAGE_CODE = "invalid_board_image";
  static final String INVALID_BOARD_IMAGE_MESSAGE = "ボードの画像は PNG で、5 MB 以下にしてください";

  private static final String POINTED_REGIONS_FIELD = "pointedRegions";

  /** ボードの画像の大きさの上限 (5 MB)。 */
  private static final long MAX_BOARD_BYTES = 5L * 1024 * 1024;

  /** 指している範囲の件数の上限 (openapi.yaml の `ReplyMaterials`)。 */
  private static final int MAX_POINTED_REGIONS = 10;

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

  /**
   * ボードの画像を読む。形式は PNG、大きさは 5 MB 以下。外れたら、中身を読まずに拒否する。
   *
   * @throws ApiException 画像が無い、PNG でない、空、または 5 MB を超える (`invalid_board_image`)
   */
  static byte[] readBoardImage(Context ctx) {
    UploadedFile file = ctx.uploadedFile(BOARD_IMAGE_FIELD);
    if (file == null || file.size() > MAX_BOARD_BYTES) {
      throw invalidBoardImage();
    }
    byte[] bytes = readAtMost(file, MAX_BOARD_BYTES);
    if (bytes.length == 0 || bytes.length > MAX_BOARD_BYTES) {
      throw invalidBoardImage();
    }
    if (ImageFormat.detect(bytes).filter(format -> format == ImageFormat.PNG).isEmpty()) {
      throw invalidBoardImage();
    }
    return bytes;
  }

  /**
   * 指している範囲 (JSON の文字列) を読む。無ければ空。件数と範囲を検査する。
   *
   * @throws ApiException 形式が不正、件数が 10 を超える、範囲が外れている (`invalid_request`)
   */
  static List<OrganizedProblem.Region> readPointedRegions(Context ctx) {
    String json = ctx.formParam(POINTED_REGIONS_FIELD);
    if (json == null || json.isBlank()) {
      return List.of();
    }
    List<OrganizedProblem.Region> regions;
    try {
      regions =
          Json.mapper().readValue(json, new TypeReference<List<OrganizedProblem.Region>>() {});
    } catch (JsonProcessingException e) {
      // Jackson の例外の文言には本文の一部が入りうるため、使わない
      throw invalidRequest();
    }
    if (regions == null
        || regions.size() > MAX_POINTED_REGIONS
        || regions.stream().anyMatch(region -> region == null || !region.fitsInImage())) {
      throw invalidRequest();
    }
    return regions;
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

  private static ApiException invalidBoardImage() {
    return ApiException.badRequest(INVALID_BOARD_IMAGE_CODE, INVALID_BOARD_IMAGE_MESSAGE);
  }

  private static ApiException invalidRequest() {
    return ApiException.badRequest("invalid_request", ApiErrors.INVALID_REQUEST_MESSAGE);
  }
}
