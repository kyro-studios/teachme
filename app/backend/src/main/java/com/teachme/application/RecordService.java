package com.teachme.application;

import com.teachme.domain.Subject;
import com.teachme.persistence.RecordRepository;
import com.teachme.persistence.RecordRepository.LessonCounts;
import com.teachme.persistence.RecordRepository.MockExamItem;
import com.teachme.persistence.RecordRepository.NoteDetail;
import com.teachme.persistence.RecordRepository.NoteItem;
import com.teachme.persistence.RecordRepository.UnitRow;
import com.teachme.persistence.RecordRepository.UnitSummaryRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 学習記録 (仕様書 5.8、5.9、アーキテクチャ設計 4.1、7.3)。授業の記録と模試の結果から、一覧、単元の画面の内容、まとめノート、学習レポートを集計する。
 *
 * <p>集計の結果は保存しない。LLM は使わない。削除は、単元の学習記録だけを消し、教材は残す。ほかの利用者の識別子は、無いものとして 404 にする。
 */
public final class RecordService {

  private final SettingsService settings;
  private final RecordRepository records;

  public RecordService(SettingsService settings, RecordRepository records) {
    this.settings = settings;
    this.records = records;
  }

  /**
   * 授業の記録か模試の結果のある単元の一覧。並びは単元の作成順。記録の無い単元は含めない。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public List<UnitSummary> listUnits(UUID userId) {
    settings.requireCompleted(userId);
    return records.unitsWithRecords(userId).stream().map(RecordService::toUnitSummary).toList();
  }

  /**
   * 単元の画面の内容。授業が 0 回でも、利用者の単元なら返す。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   * @throws NotFoundException 無い単元、またはほかの利用者の単元のとき
   */
  public RecordUnitView getUnit(UUID userId, UUID unitId) {
    settings.requireCompleted(userId);
    UnitRow unit = records.findUnit(userId, unitId).orElseThrow(NotFoundException::new);
    List<MockExamEntry> mockExams =
        records.mockExams(unitId).stream().map(RecordService::toMockExamEntry).toList();
    LessonCounts counts = records.lessonCounts(unitId);
    // 模試の結果は新しい順に並ぶため、先頭が最新の模試になる
    MockExamEntry latest = mockExams.isEmpty() ? null : mockExams.get(0);
    return new RecordUnitView(
        unit.unitId(),
        unit.subject(),
        unit.unitName(),
        records.notes(unitId).stream().map(RecordService::toNoteEntry).toList(),
        mockExams,
        new LearningReport(
            unit.unitName(), counts.lessonCount(), counts.taughtProblemCount(), latest));
  }

  /**
   * 単元の学習記録を消す。授業の記録、まとめノート、模試の結果を消し、理解度と模試の機会の数えを 0 に戻す。教材は残す。
   *
   * <p>記録が無くても成功する (削除は冪等)。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   * @throws NotFoundException 無い単元、またはほかの利用者の単元のとき。何も消さない
   */
  public void deleteUnit(UUID userId, UUID unitId) {
    settings.requireCompleted(userId);
    if (!records.deleteUnitRecords(userId, unitId)) {
      throw new NotFoundException();
    }
  }

  /**
   * 保存済みのまとめノート。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   * @throws NotFoundException 無いまとめノート、またはほかの利用者のものが指されたとき
   */
  public SummaryNoteView getNote(UUID userId, UUID recordId) {
    settings.requireCompleted(userId);
    NoteDetail note = records.findNote(userId, recordId).orElseThrow(NotFoundException::new);
    return new SummaryNoteView(
        note.recordId(), note.unitName(), note.completedAt(), note.summaryText(), note.quotes());
  }

  private static UnitSummary toUnitSummary(UnitSummaryRow row) {
    return new UnitSummary(
        row.unitId(), row.subject(), row.unitName(), row.lessonCount(), row.mockExamCount());
  }

  private static NoteEntry toNoteEntry(NoteItem item) {
    return new NoteEntry(item.recordId(), item.completedAt());
  }

  private static MockExamEntry toMockExamEntry(MockExamItem item) {
    return new MockExamEntry(item.id(), item.takenAt(), item.score(), item.deviation());
  }

  /**
   * 記録のある単元 1 件 (一覧の項目)。
   *
   * @param lessonCount 授業の完了数
   * @param mockExamCount 模試の結果の数
   */
  public record UnitSummary(
      UUID unitId, Subject subject, String unitName, int lessonCount, int mockExamCount) {}

  /**
   * 単元の画面の内容。
   *
   * @param notes まとめノートの一覧 (新しい順)
   * @param mockExams 模試の結果の一覧 (新しい順)。偏差値の推移は、この一覧の古い順に読む
   * @param report 学習レポート
   */
  public record RecordUnitView(
      UUID unitId,
      Subject subject,
      String unitName,
      List<NoteEntry> notes,
      List<MockExamEntry> mockExams,
      LearningReport report) {}

  /** まとめノートの一覧の項目。 */
  public record NoteEntry(UUID recordId, Instant completedAt) {}

  /** 模試の結果の一覧の項目。 */
  public record MockExamEntry(UUID id, Instant takenAt, int score, BigDecimal deviation) {}

  /**
   * 学習レポート (仕様書 5.8)。理解度は含めない。
   *
   * @param lessonCount 単元の授業の完了数の合計
   * @param taughtProblemCount 授業に使った教材の数。同じ教材の重複は 1 つと数える
   * @param latestMockExam 最新の模試。模試が無いときは null
   */
  public record LearningReport(
      String unitName, int lessonCount, int taughtProblemCount, MockExamEntry latestMockExam) {}

  /** まとめノート 1 件。 */
  public record SummaryNoteView(
      UUID recordId,
      String unitName,
      Instant completedAt,
      String summaryText,
      List<String> quotes) {}
}
