package com.teachme.web;

import com.teachme.application.TestControlService;
import com.teachme.application.TestControlService.ScriptedAttempt;
import com.teachme.domain.DialogueLimits;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * テスト用の API (`/api/test/*`)。`AI_MODE=fake` のときだけ、bootstrap が登録する (`real` では登録せず、404 を返す)。
 *
 * <p>利用者は Cookie で識別し、呼んだ利用者の分だけに効く。
 */
public final class TestRoutes implements Routes {

  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_FAILURE = "failure";
  private static final String VALIDATION_CODE = "validation_error";

  /** `GET /api/test/ai-calls` の応答。 */
  public record AiCallsResponse(Map<String, Integer> counts) {}

  /** `PUT /api/test/ai-script` の本文。 */
  public record AiScriptRequest(String kind, Boolean repeat, List<AiScriptResponse> responses) {}

  /** 応答 1 件。`outcome` は `success` か `failure`。 */
  public record AiScriptResponse(String outcome, Map<String, Object> payload) {}

  /** `PUT /api/test/limits` の本文。省いた項目は、既定の 600 秒に戻す。 */
  public record LimitsRequest(Integer lessonSeconds, Integer consultationSeconds) {}

  private final TestControlService testControl;

  public TestRoutes(TestControlService testControl) {
    this.testControl = testControl;
  }

  @Override
  public void register(Javalin app) {
    app.put("/api/test/ai-script", this::putAiScript);
    app.delete("/api/test/ai-script", this::deleteAiScript);
    app.get("/api/test/ai-calls", this::getAiCalls);
    app.put("/api/test/limits", this::putLimits);
  }

  private void putAiScript(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    AiScriptRequest request = RequestBodies.readJson(ctx, AiScriptRequest.class);
    if (request == null || request.kind() == null || request.kind().isBlank()) {
      throw ApiException.badRequest(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);
    }
    if (request.responses() == null
        || request.responses().isEmpty()
        || request.responses().stream().anyMatch(TestRoutes::isInvalidResponse)) {
      throw ApiException.badRequest(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);
    }

    List<ScriptedAttempt> attempts =
        request.responses().stream()
            .map(
                response ->
                    new ScriptedAttempt(
                        OUTCOME_SUCCESS.equals(response.outcome()), response.payload()))
            .toList();
    try {
      testControl.scriptAi(userId, request.kind(), Boolean.TRUE.equals(request.repeat()), attempts);
    } catch (IllegalArgumentException e) {
      // 知らない kind と、payload の形の誤り。例外の文言には入力の中身が入りうるため、使わない
      throw ApiException.badRequest(VALIDATION_CODE, "テスト用の AI の応答の指定が正しくありません");
    }
    ctx.status(204);
  }

  private void deleteAiScript(Context ctx) {
    testControl.resetAi(UserCookie.userId(ctx));
    ctx.status(204);
  }

  private void getAiCalls(Context ctx) {
    ctx.json(new AiCallsResponse(testControl.aiCalls(UserCookie.userId(ctx))));
  }

  private void putLimits(Context ctx) {
    UUID userId = UserCookie.userId(ctx);
    LimitsRequest request = RequestBodies.readJson(ctx, LimitsRequest.class);
    if (request == null) {
      throw ApiException.badRequest(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);
    }
    FieldErrors errors = new FieldErrors();
    validateSeconds(errors, "lessonSeconds", request.lessonSeconds());
    validateSeconds(errors, "consultationSeconds", request.consultationSeconds());
    errors.throwIfAny(VALIDATION_CODE, ApiErrors.INVALID_REQUEST_MESSAGE);

    testControl.setLimits(userId, request.lessonSeconds(), request.consultationSeconds());
    ctx.status(204);
  }

  /** 応答が無い、または `outcome` が `success` と `failure` のどちらでもないとき。 */
  private static boolean isInvalidResponse(AiScriptResponse response) {
    return response == null
        || !(OUTCOME_SUCCESS.equals(response.outcome())
            || OUTCOME_FAILURE.equals(response.outcome()));
  }

  private static void validateSeconds(FieldErrors errors, String field, Integer seconds) {
    if (seconds != null
        && (seconds < DialogueLimits.MIN_SECONDS || seconds > DialogueLimits.MAX_SECONDS)) {
      errors.add(field, "1 から 600 の秒数で指定してください");
    }
  }
}
