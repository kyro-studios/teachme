package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teachme.application.ConsultationService;
import com.teachme.application.DialogueService.Recognition;
import com.teachme.application.DialogueService.Recording;
import com.teachme.domain.BoardWrite;
import com.teachme.domain.OrganizedProblem;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 先輩先生への相談 (`/api/consultations`、openapi.yaml の consultations)。検査と変換だけを行い、進行は {@link
 * ConsultationService} に任せる。
 *
 * <p>録音、ボードの画像、指している範囲の検査は、授業と共通の {@link DialogueRequests} を使う。
 */
public final class ConsultationRoutes implements Routes {

  private static final String AUDIO_MIME_TYPE = "audio/mpeg";

  /** `POST /consultations` の本文。 */
  public record StartConsultationRequest(String materialId) {}

  /** 先輩先生の字幕 1 行 (openapi.yaml の `Turn`)。相談の字幕は、話し手が先輩先生で、表情は無い。 */
  public record TurnResponse(String turnId, String speaker, String text) {

    static TurnResponse mentor(ConsultationService.Turn turn) {
      return new TurnResponse(turn.turnId().toString(), "mentor", turn.text());
    }
  }

  /** 相談の開始の応答 (openapi.yaml の `ConsultationStart`)。模範解答、解説、指導のポイントは含めない。 */
  public record ConsultationStartResponse(
      String consultationId,
      Instant startedAt,
      Instant serverNow,
      Instant deadlineAt,
      int maxRounds,
      String problemText,
      List<OrganizedProblem.Figure> figures) {

    static ConsultationStartResponse of(ConsultationService.Started started) {
      return new ConsultationStartResponse(
          started.consultationId().toString(),
          started.startedAt(),
          started.serverNow(),
          started.deadlineAt(),
          started.maxRounds(),
          started.problemText(),
          started.figures());
    }
  }

  /** 認識の結果 (openapi.yaml の `UtteranceResult`)。認識できなかったときは、`utteranceId` と `text` を書かない。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UtteranceResponse(boolean recognized, String utteranceId, String text) {}

  /** ボードへの書き込み (openapi.yaml の `BoardWrite`)。文字の書き込みは `text`、範囲の強調は `region` を持つ。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record BoardWriteResponse(String kind, String text, OrganizedProblem.Region region) {

    static BoardWriteResponse of(BoardWrite write) {
      return new BoardWriteResponse(write.kind().wireName(), write.text(), write.region());
    }
  }

  /** 先輩先生の応答 (openapi.yaml の `ConsultationReply`)。 */
  public record ConsultationReplyResponse(
      TurnResponse turn,
      List<BoardWriteResponse> boardWrites,
      int roundCount,
      boolean limitReached) {}

  private final ConsultationService consultations;

  public ConsultationRoutes(ConsultationService consultations) {
    this.consultations = consultations;
  }

  @Override
  public void register(Javalin app) {
    app.post("/api/consultations", this::startConsultation);
    app.post("/api/consultations/{consultationId}/utterances", this::sendUtterance);
    app.post("/api/consultations/{consultationId}/utterances/{utteranceId}/reply", this::reply);
    app.get("/api/consultations/{consultationId}/turns/{turnId}/audio", this::audio);
    app.post("/api/consultations/{consultationId}/abandon", this::abandon);
  }

  private void startConsultation(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    StartConsultationRequest request = RequestBodies.readJson(ctx, StartConsultationRequest.class);
    UUID materialId =
        parseUuid(request.materialId())
            .orElseThrow(
                () ->
                    ApiException.badRequest("invalid_request", ApiErrors.INVALID_REQUEST_MESSAGE));
    ConsultationService.Started started = consultations.start(userId, materialId);
    ctx.status(201).json(ConsultationStartResponse.of(started));
  }

  private void sendUtterance(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> consultationId = pathUuid(ctx, "consultationId");
    if (consultationId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    Recording recording = DialogueRequests.readRecording(ctx);
    Optional<Recognition> recognized = consultations.utter(userId, consultationId.get(), recording);
    ctx.json(
        recognized
            .map(
                result ->
                    new UtteranceResponse(true, result.utteranceId().toString(), result.text()))
            .orElse(new UtteranceResponse(false, null, null)));
  }

  private void reply(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> consultationId = pathUuid(ctx, "consultationId");
    Optional<UUID> utteranceId = pathUuid(ctx, "utteranceId");
    if (consultationId.isEmpty() || utteranceId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    byte[] board = DialogueRequests.readBoardImage(ctx);
    List<OrganizedProblem.Region> pointedRegions = DialogueRequests.readPointedRegions(ctx);
    ConsultationService.Reply reply =
        consultations.reply(userId, consultationId.get(), utteranceId.get(), board, pointedRegions);
    ctx.json(
        new ConsultationReplyResponse(
            TurnResponse.mentor(reply.turn()),
            reply.boardWrites().stream().map(BoardWriteResponse::of).toList(),
            reply.roundCount(),
            reply.limitReached()));
  }

  private void audio(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    Optional<UUID> consultationId = pathUuid(ctx, "consultationId");
    Optional<UUID> turnId = pathUuid(ctx, "turnId");
    if (consultationId.isEmpty() || turnId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    byte[] audio = consultations.audio(userId, consultationId.get(), turnId.get());
    ctx.contentType(AUDIO_MIME_TYPE).result(audio);
  }

  private void abandon(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    // 相談が無い、または形式が不正でも、中断は済んでいるものとして返す
    pathUuid(ctx, "consultationId")
        .ifPresent(consultationId -> consultations.abandon(userId, consultationId));
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
}
