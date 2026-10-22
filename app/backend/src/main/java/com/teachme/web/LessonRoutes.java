package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.teachme.application.DialogueService.Recognition;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.DialogueSweeper;
import com.teachme.application.LessonService;
import com.teachme.domain.Annotation;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.OrganizedProblem;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 授業 (`/api/lessons`、openapi.yaml の lessons)。検査と変換だけを行い、進行は {@link LessonService} に任せる。
 *
 * <p>進行中の授業と相談の掃除は、サーバーの起動と停止に合わせて開始と終了する。
 */
public final class LessonRoutes implements Routes {

  static final String INVALID_BOARD_IMAGE_CODE = "invalid_board_image";
  static final String INVALID_BOARD_IMAGE_MESSAGE = "ボードの画像は PNG で、5 MB 以下にしてください";

  private static final String BOARD_IMAGE_FIELD = "boardImage";
  private static final String POINTED_REGIONS_FIELD = "pointedRegions";
  private static final String AUDIO_MIME_TYPE = "audio/mpeg";

  /** ボードの画像の大きさの上限 (5 MB)。 */
  private static final long MAX_BOARD_BYTES = 5L * 1024 * 1024;

  /** 指している範囲の件数の上限 (openapi.yaml の `ReplyMaterials`)。 */
  private static final int MAX_POINTED_REGIONS = 10;

  /** `POST /lessons` の本文。 */
  public record StartLessonRequest(String materialId) {}

  /** AI 生徒の字幕 1 行 (openapi.yaml の `Turn`)。授業の字幕は、話し手が生徒。 */
  public record TurnResponse(String turnId, String speaker, String text, String expression) {

    static TurnResponse student(LessonService.Turn turn) {
      return new TurnResponse(
          turn.turnId().toString(), "student", turn.text(), turn.expression().wireName());
    }
  }

  /** 授業の開始の応答 (openapi.yaml の `LessonStart`)。模範解答と解説は含めない。 */
  public record LessonStartResponse(
      String lessonId,
      Instant startedAt,
      Instant serverNow,
      Instant deadlineAt,
      int maxRounds,
      SettingsRoutes.StudentTypeView student,
      String problemText,
      List<OrganizedProblem.Figure> figures,
      String studentWork,
      TurnResponse opening) {

    static LessonStartResponse of(LessonService.Started started) {
      return new LessonStartResponse(
          started.lessonId().toString(),
          started.startedAt(),
          started.serverNow(),
          started.deadlineAt(),
          started.maxRounds(),
          SettingsRoutes.StudentTypeView.of(started.student()),
          started.board().problemText(),
          started.board().figures(),
          started.board().studentWork(),
          TurnResponse.student(started.opening()));
    }
  }

  /** 認識の結果 (openapi.yaml の `UtteranceResult`)。認識できなかったときは、`utteranceId` と `text` を書かない。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UtteranceResponse(boolean recognized, String utteranceId, String text) {}

  /** ボードに示す印 (openapi.yaml の `StudentAnnotation`)。 */
  public record AnnotationResponse(String kind, OrganizedProblem.Region region) {

    static AnnotationResponse of(Annotation annotation) {
      return new AnnotationResponse(annotation.kind().wireName(), annotation.region());
    }
  }

  /** AI 生徒の応答 (openapi.yaml の `LessonReply`)。印が無いときは `annotation` を null で書く。 */
  public record LessonReplyResponse(
      TurnResponse turn,
      @JsonInclude(JsonInclude.Include.ALWAYS) AnnotationResponse annotation,
      int roundCount,
      boolean limitReached) {}

  /** 記録したまとめノート (openapi.yaml の `LessonSummary`)。 */
  public record LessonSummaryResponse(
      String recordId,
      Instant completedAt,
      String summaryText,
      List<String> quotes,
      int lessonsThisWeek) {

    static LessonSummaryResponse of(LessonService.Summary summary) {
      return new LessonSummaryResponse(
          summary.recordId().toString(),
          summary.completedAt(),
          summary.summaryText(),
          summary.quotes(),
          summary.lessonsThisWeek());
    }
  }

  private final LessonService lessons;
  private final DialogueSweeper sweeper;

  public LessonRoutes(LessonService lessons, DialogueSweeper sweeper) {
    this.lessons = lessons;
    this.sweeper = sweeper;
  }

  @Override
  public void register(Javalin app) {
    app.events(
        events -> {
          events.serverStarting(sweeper::start);
          events.serverStopping(sweeper::close);
        });
    app.post("/api/lessons", this::startLesson);
    app.post("/api/lessons/{lessonId}/utterances", this::sendUtterance);
    app.post("/api/lessons/{lessonId}/utterances/{utteranceId}/reply", this::reply);
    app.get("/api/lessons/{lessonId}/turns/{turnId}/audio", this::audio);
    app.post("/api/lessons/{lessonId}/summary", this::summarize);
    app.post("/api/lessons/{lessonId}/abandon", this::abandon);
  }

  private void startLesson(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    StartLessonRequest request = RequestBodies.readJson(ctx, StartLessonRequest.class);
    UUID materialId =
        parseUuid(request.materialId())
            .orElseThrow(
                () ->
                    ApiException.badRequest("invalid_request", ApiErrors.INVALID_REQUEST_MESSAGE));
    LessonService.Started started = lessons.start(userId, materialId);
    ctx.status(201).json(LessonStartResponse.of(started));
  }

  private void sendUtterance(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> lessonId = pathUuid(ctx, "lessonId");
    if (lessonId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    Recording recording = DialogueRequests.readRecording(ctx);
    Optional<Recognition> recognized = lessons.utter(userId, lessonId.get(), recording);
    ctx.json(
        recognized
            .map(
                result ->
                    new UtteranceResponse(true, result.utteranceId().toString(), result.text()))
            .orElse(new UtteranceResponse(false, null, null)));
  }

  private void reply(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> lessonId = pathUuid(ctx, "lessonId");
    Optional<UUID> utteranceId = pathUuid(ctx, "utteranceId");
    if (lessonId.isEmpty() || utteranceId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    byte[] board = readBoardImage(ctx);
    List<OrganizedProblem.Region> pointedRegions = readPointedRegions(ctx);
    LessonService.Reply reply =
        lessons.reply(userId, lessonId.get(), utteranceId.get(), board, pointedRegions);
    ctx.json(
        new LessonReplyResponse(
            TurnResponse.student(reply.turn()),
            reply.annotation().map(AnnotationResponse::of).orElse(null),
            reply.roundCount(),
            reply.limitReached()));
  }

  private void audio(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> lessonId = pathUuid(ctx, "lessonId");
    Optional<UUID> turnId = pathUuid(ctx, "turnId");
    if (lessonId.isEmpty() || turnId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    byte[] audio = lessons.audio(userId, lessonId.get(), turnId.get());
    ctx.contentType(AUDIO_MIME_TYPE).result(audio);
  }

  private void summarize(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> lessonId = pathUuid(ctx, "lessonId");
    if (lessonId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    byte[] board = readBoardImage(ctx);
    LessonService.Summary summary = lessons.summarize(userId, lessonId.get(), board);
    ctx.json(LessonSummaryResponse.of(summary));
  }

  private void abandon(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    // 授業が無い、または形式が不正でも、中断は済んでいるものとして返す
    pathUuid(ctx, "lessonId").ifPresent(lessonId -> lessons.abandon(userId, lessonId));
    ctx.status(204);
  }

  /** パスの識別子。形式が不正なら空にし、無い対象として扱う。 */
  private static Optional<UUID> pathUuid(Context ctx, String name) {
    return parseUuid(ctx.pathParam(name));
  }

  private static Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }

  /**
   * ボードの画像を読む。形式は PNG、大きさは 5 MB 以下。外れたら、中身を読まずに拒否する。
   *
   * @throws ApiException 画像が無い、PNG でない、空、または 5 MB を超える (`invalid_board_image`)
   */
  private static byte[] readBoardImage(Context ctx) {
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
  private static List<OrganizedProblem.Region> readPointedRegions(Context ctx) {
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

  /** 上限より 1 バイト多く読み、超えたかどうかを判定できるようにする。 */
  private static byte[] readAtMost(UploadedFile file, long maxBytes) {
    try (InputStream content = file.content()) {
      return content.readNBytes((int) maxBytes + 1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ApiException invalidBoardImage() {
    return ApiException.badRequest(INVALID_BOARD_IMAGE_CODE, INVALID_BOARD_IMAGE_MESSAGE);
  }

  private static ApiException invalidRequest() {
    return ApiException.badRequest("invalid_request", ApiErrors.INVALID_REQUEST_MESSAGE);
  }
}
