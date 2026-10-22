package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teachme.application.RecordService;
import com.teachme.application.RecordService.LearningReport;
import com.teachme.application.RecordService.MockExamEntry;
import com.teachme.application.RecordService.NoteEntry;
import com.teachme.application.RecordService.RecordUnitView;
import com.teachme.application.RecordService.SummaryNoteView;
import com.teachme.application.RecordService.UnitSummary;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 学習記録 (`/api/records`、openapi.yaml の records)。検査と変換だけを行い、集計と削除は {@link RecordService} に任せる。 */
public final class RecordRoutes implements Routes {

  /** 記録のある単元 1 件 (openapi.yaml の `RecordUnits` の項目)。 */
  public record UnitSummaryResponse(
      String unitId, String subject, String unitName, int lessonCount, int mockExamCount) {

    static UnitSummaryResponse of(UnitSummary unit) {
      return new UnitSummaryResponse(
          unit.unitId().toString(),
          unit.subject().id(),
          unit.unitName(),
          unit.lessonCount(),
          unit.mockExamCount());
    }
  }

  /** 記録のある単元の一覧 (openapi.yaml の `records/units` の応答)。 */
  public record UnitListResponse(List<UnitSummaryResponse> units) {}

  /** まとめノートの一覧の項目 (openapi.yaml の `RecordUnit.notes`)。 */
  public record NoteEntryResponse(String recordId, Instant completedAt) {

    static NoteEntryResponse of(NoteEntry note) {
      return new NoteEntryResponse(note.recordId().toString(), note.completedAt());
    }
  }

  /** 模試の結果の一覧の項目 (openapi.yaml の `RecordUnit.mockExams`)。 */
  public record MockExamEntryResponse(String id, Instant takenAt, int score, BigDecimal deviation) {

    static MockExamEntryResponse of(MockExamEntry mockExam) {
      return new MockExamEntryResponse(
          mockExam.id().toString(), mockExam.takenAt(), mockExam.score(), mockExam.deviation());
    }
  }

  /** 最新の模試の得点と偏差値 (openapi.yaml の `LearningReport.latestMockExam`)。 */
  public record LatestMockExamResponse(int score, BigDecimal deviation) {}

  /** 学習レポート (openapi.yaml の `LearningReport`)。模試が無いときは `latestMockExam` を null で書く。 */
  public record LearningReportResponse(
      String unitName,
      int lessonCount,
      int taughtProblemCount,
      @JsonInclude(JsonInclude.Include.ALWAYS) LatestMockExamResponse latestMockExam) {

    static LearningReportResponse of(LearningReport report) {
      MockExamEntry latest = report.latestMockExam();
      return new LearningReportResponse(
          report.unitName(),
          report.lessonCount(),
          report.taughtProblemCount(),
          latest == null ? null : new LatestMockExamResponse(latest.score(), latest.deviation()));
    }
  }

  /** 単元の画面の内容 (openapi.yaml の `RecordUnit`)。 */
  public record RecordUnitResponse(
      String unitId,
      String subject,
      String unitName,
      List<NoteEntryResponse> notes,
      List<MockExamEntryResponse> mockExams,
      LearningReportResponse report) {

    static RecordUnitResponse of(RecordUnitView view) {
      return new RecordUnitResponse(
          view.unitId().toString(),
          view.subject().id(),
          view.unitName(),
          view.notes().stream().map(NoteEntryResponse::of).toList(),
          view.mockExams().stream().map(MockExamEntryResponse::of).toList(),
          LearningReportResponse.of(view.report()));
    }
  }

  /** まとめノート (openapi.yaml の `SummaryNote`)。 */
  public record SummaryNoteResponse(
      String recordId,
      String unitName,
      Instant completedAt,
      String summaryText,
      List<String> quotes) {

    static SummaryNoteResponse of(SummaryNoteView view) {
      return new SummaryNoteResponse(
          view.recordId().toString(),
          view.unitName(),
          view.completedAt(),
          view.summaryText(),
          view.quotes());
    }
  }

  private final RecordService records;

  public RecordRoutes(RecordService records) {
    this.records = records;
  }

  @Override
  public void register(Javalin app) {
    app.get("/api/records/units", this::listUnits);
    app.get("/api/records/units/{unitId}", this::getUnit);
    app.delete("/api/records/units/{unitId}", this::deleteUnit);
    app.get("/api/records/notes/{recordId}", this::getNote);
  }

  private void listUnits(Context ctx) {
    List<UnitSummaryResponse> units =
        records.listUnits(UserCookie.userId(ctx)).stream().map(UnitSummaryResponse::of).toList();
    ctx.json(new UnitListResponse(units));
  }

  private void getUnit(Context ctx) {
    Optional<UUID> unitId = pathUuid(ctx, "unitId");
    if (unitId.isEmpty()) {
      // 形式が不正な識別子は、無い単元として扱う
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.json(RecordUnitResponse.of(records.getUnit(UserCookie.userId(ctx), unitId.get())));
  }

  private void deleteUnit(Context ctx) {
    Optional<UUID> unitId = pathUuid(ctx, "unitId");
    if (unitId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    records.deleteUnit(UserCookie.userId(ctx), unitId.get());
    ctx.status(204);
  }

  private void getNote(Context ctx) {
    Optional<UUID> recordId = pathUuid(ctx, "recordId");
    if (recordId.isEmpty()) {
      ApiErrors.notFound(ctx);
      return;
    }
    ctx.json(SummaryNoteResponse.of(records.getNote(UserCookie.userId(ctx), recordId.get())));
  }

  private static Optional<UUID> pathUuid(Context ctx, String name) {
    try {
      return Optional.of(UUID.fromString(ctx.pathParam(name)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
