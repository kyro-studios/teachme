package com.teachme.web;

import com.teachme.application.MockExamService;
import com.teachme.application.MockExamService.DeviationPoint;
import com.teachme.application.MockExamService.MockExamView;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 模試 (`/api/mock-exams`、openapi.yaml の mock-exams)。検査と変換だけを行い、挑戦は {@link MockExamService} に任せる。 */
public final class MockExamRoutes implements Routes {

  /** 偏差値の推移の 1 点 (openapi.yaml の `MockExamResult.history`)。 */
  public record HistoryPointResponse(Instant takenAt, BigDecimal deviation) {

    static HistoryPointResponse of(DeviationPoint point) {
      return new HistoryPointResponse(point.takenAt(), point.deviation());
    }
  }

  /** 模試の結果 (openapi.yaml の `MockExamResult`)。 */
  public record MockExamResponse(
      String id,
      Instant takenAt,
      String unitName,
      int score,
      BigDecimal deviation,
      String problemText,
      String answerSheet,
      String studentComment,
      List<HistoryPointResponse> history) {

    static MockExamResponse of(MockExamView view) {
      return new MockExamResponse(
          view.id().toString(),
          view.takenAt(),
          view.unitName(),
          view.score(),
          view.deviation(),
          view.problemText(),
          view.answerSheet(),
          view.studentComment(),
          view.history().stream().map(HistoryPointResponse::of).toList());
    }
  }

  private final MockExamService mockExams;

  public MockExamRoutes(MockExamService mockExams) {
    this.mockExams = mockExams;
  }

  @Override
  public void register(Javalin app) {
    app.post("/api/mock-exams", this::take);
    app.get("/api/mock-exams/{mockExamId}", this::get);
  }

  private void take(Context ctx) {
    ctx.status(201).json(MockExamResponse.of(mockExams.take(UserCookie.userId(ctx))));
  }

  private void get(Context ctx) {
    Optional<UUID> mockExamId = parseUuid(ctx.pathParam("mockExamId"));
    if (mockExamId.isEmpty()) {
      // 形式が不正な識別子は、無い模試として扱う
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.json(MockExamResponse.of(mockExams.get(UserCookie.userId(ctx), mockExamId.get())));
  }

  private static Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
