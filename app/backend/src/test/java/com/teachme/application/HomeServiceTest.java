package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.application.HomeService.HomeView;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.TodayMode;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private DataSource dataSource;
  private Repositories repositories;
  private SettingsService settings;
  private HomeService home;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    Clock clock = Fixtures.clockAt("2026-10-21T10:00");
    settings = new SettingsService(repositories.settings(), clock);
    home = homeAt("2026-10-21T10:00");
  }

  /** 指定した日時の時計で、ホームのサービスを作る。DB は同じものを使う。 */
  private HomeService homeAt(String localDateTime) {
    return new HomeService(
        settings,
        repositories.lessonRecords(),
        repositories.todayModes(),
        repositories.units(),
        Fixtures.clockAt(localDateTime));
  }

  /** 利用者の設定を保存し、現在の単元の識別子を返す。 */
  private UUID completeSetup(UUID userId) {
    return settings
        .save(
            userId,
            new SettingsCommand(
                StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true))
        .unitId();
  }

  private static Instant jst(String localDateTime) {
    return ZonedDateTime.parse(localDateTime + "+09:00[Asia/Tokyo]").toInstant();
  }

  @Test
  void 設定が未完了ならホームは_setup_required_になる() {
    ConflictException error = assertThrows(ConflictException.class, () -> home.home(USER));

    assertEquals("setup_required", error.code());
  }

  @Test
  void 設定の後のホームは_担当と単元と今週の授業と通常のモードを返す() {
    completeSetup(USER);

    HomeView view = home.home(USER);

    assertEquals(StudentType.LOGICAL, view.student());
    assertEquals(Subject.MATH, view.subject());
    assertEquals("二次関数", view.unitName());
    assertEquals(0, view.lessonsThisWeek());
    assertEquals(3, view.lessonsPerWeek());
    assertFalse(view.mockExamAvailable());
    assertEquals(TodayMode.NORMAL, view.todayMode());
  }

  @Test
  void 今週の授業は_月曜_0_時から_次の月曜_0_時の手前までを数える() {
    UUID unitId = completeSetup(USER);
    Fixtures.insertLessonRecord(dataSource, USER, unitId, jst("2026-10-18T23:59:59.999"));
    Fixtures.insertLessonRecord(dataSource, USER, unitId, jst("2026-10-19T00:00:00"));
    Fixtures.insertLessonRecord(dataSource, USER, unitId, jst("2026-10-25T23:59:59.999"));
    Fixtures.insertLessonRecord(dataSource, USER, unitId, jst("2026-10-26T00:00:00"));

    assertEquals(2, home.home(USER).lessonsThisWeek());
  }

  @Test
  void 今週の授業は_ほかの利用者の記録を数えない() {
    UUID unitId = completeSetup(USER);
    UUID otherUnitId = completeSetup(OTHER);
    Fixtures.insertLessonRecord(dataSource, OTHER, otherUnitId, jst("2026-10-20T10:00:00"));
    Fixtures.insertLessonRecord(dataSource, USER, unitId, jst("2026-10-20T10:00:00"));

    assertEquals(1, home.home(USER).lessonsThisWeek());
    assertEquals(1, home.home(OTHER).lessonsThisWeek());
  }

  @Test
  void 模試の機会は_授業の完了が_3_回に達すると出る() {
    UUID unitId = completeSetup(USER);
    Fixtures.setLessonsSinceExam(dataSource, unitId, 2);
    assertFalse(home.home(USER).mockExamAvailable());

    Fixtures.setLessonsSinceExam(dataSource, unitId, 3);
    assertTrue(home.home(USER).mockExamAvailable());
  }

  @Test
  void 今日のモードは_保存した日に限り有効で_日付が変わると通常に戻る() {
    completeSetup(USER);

    HomeView switched = home.setTodayMode(USER, TodayMode.SELF_STUDY);

    assertEquals(TodayMode.SELF_STUDY, switched.todayMode());
    assertEquals(TodayMode.SELF_STUDY, home.home(USER).todayMode());
    assertEquals(TodayMode.NORMAL, homeAt("2026-10-22T09:00").home(USER).todayMode());
  }

  @Test
  void 今日のモードは_ほかの利用者に影響しない() {
    completeSetup(USER);
    completeSetup(OTHER);

    home.setTodayMode(USER, TodayMode.HOMEWORK_CHECK);

    assertEquals(TodayMode.NORMAL, home.home(OTHER).todayMode());
  }
}
