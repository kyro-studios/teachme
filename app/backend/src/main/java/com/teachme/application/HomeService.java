package com.teachme.application;

import com.teachme.domain.MockExamOpportunity;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.TodayMode;
import com.teachme.domain.UserSettings;
import com.teachme.domain.WeekRange;
import com.teachme.persistence.LessonRecordRepository;
import com.teachme.persistence.TodayModeRepository;
import com.teachme.persistence.UnitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** ホーム (仕様書 5.2)。今週の授業、今日のモード、模試の機会を集めて返す。 */
public final class HomeService {

  private final SettingsService settings;
  private final LessonRecordRepository lessonRecords;
  private final TodayModeRepository todayModes;
  private final UnitRepository units;
  private final Clock clock;

  public HomeService(
      SettingsService settings,
      LessonRecordRepository lessonRecords,
      TodayModeRepository todayModes,
      UnitRepository units,
      Clock clock) {
    this.settings = settings;
    this.lessonRecords = lessonRecords;
    this.todayModes = todayModes;
    this.units = units;
    this.clock = clock;
  }

  /**
   * ホームの表示内容。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public HomeView home(UUID userId) {
    return view(userId, settings.requireCompleted(userId));
  }

  /**
   * 今日のモードを切り替え、切り替えた後のホームの表示内容を返す。確認は出さない (仕様書 5.2)。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   */
  public HomeView setTodayMode(UUID userId, TodayMode mode) {
    UserSettings current = settings.requireCompleted(userId);
    todayModes.save(userId, mode, LocalDate.now(clock));
    return view(userId, current);
  }

  private HomeView view(UUID userId, UserSettings current) {
    WeekRange week = WeekRange.containing(clock.instant(), clock.getZone());
    TodayMode mode = todayModes.effectiveMode(userId, LocalDate.now(clock));
    return new HomeView(
        current.studentType(),
        current.subject(),
        current.unitId(),
        current.unitName(),
        lessonRecords.countCompletedIn(userId, week),
        current.lessonsPerWeek(),
        MockExamOpportunity.isAvailable(units.lessonsSinceExam(current.unitId())),
        mode,
        units.hasOrganizedMaterial(current.unitId()));
  }

  /**
   * ホームの表示内容。
   *
   * @param lessonsThisWeek 今週に記録した授業の回数 (全単元を合わせる)
   * @param mockExamAvailable 現在の単元に模試の機会があるか
   * @param hasOrganizedMaterial 現在の単元に、整理済みの教材が 1 つ以上あるか
   */
  public record HomeView(
      StudentType student,
      Subject subject,
      UUID unitId,
      String unitName,
      int lessonsThisWeek,
      int lessonsPerWeek,
      boolean mockExamAvailable,
      TodayMode todayMode,
      boolean hasOrganizedMaterial) {}
}
