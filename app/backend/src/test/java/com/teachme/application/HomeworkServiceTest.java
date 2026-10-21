package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.HomeworkService.Verdict;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.TodayMode;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeworkServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final byte[] PHOTO = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  private Repositories repositories;
  private SettingsService settings;
  private FakeAi fakeAi;
  private HomeworkService homework;

  @BeforeEach
  void setUp() {
    repositories = new Repositories(Fixtures.freshDataSource());
    var clock = Fixtures.clockAt("2026-10-21T10:00");
    settings = new SettingsService(repositories.settings(), clock);
    fakeAi = new FakeAi();
    homework = new HomeworkService(settings, repositories.todayModes(), fakeAi, clock);
  }

  private void completeSetup() {
    settings.save(
        USER,
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true));
  }

  @Test
  void 設定が未完了なら_setup_required_になる() {
    ConflictException error =
        assertThrows(ConflictException.class, () -> homework.check(USER, ImageFormat.PNG, PHOTO));

    assertEquals("setup_required", error.code());
  }

  @Test
  void 今日のモードが宿題の丸つけでなければ_mode_not_homework_check_になる() {
    completeSetup();

    ConflictException error =
        assertThrows(ConflictException.class, () -> homework.check(USER, ImageFormat.PNG, PHOTO));

    assertEquals("mode_not_homework_check", error.code());
  }

  @Test
  void 宿題の丸つけでは_外部_AI_の判定を返す() {
    completeSetup();
    repositories.todayModes().save(USER, TodayMode.HOMEWORK_CHECK, LocalDate.of(2026, 10, 21));
    fakeAi.script(
        USER,
        AiCallKind.HOMEWORK,
        List.of(new ScriptedResponse(true, Map.of("verdict", "incorrect", "comment", "惜しい"))),
        false);

    HomeworkService.Result result = homework.check(USER, ImageFormat.PNG, PHOTO);

    assertEquals(Verdict.INCORRECT, result.verdict());
    assertEquals("惜しい", result.comment());
  }

  @Test
  void 外部_AI_が失敗したら_判定できなかったものとして返す() {
    completeSetup();
    repositories.todayModes().save(USER, TodayMode.HOMEWORK_CHECK, LocalDate.of(2026, 10, 21));
    fakeAi.script(USER, AiCallKind.HOMEWORK, List.of(new ScriptedResponse(false, null)), false);

    HomeworkService.Result result = homework.check(USER, ImageFormat.PNG, PHOTO);

    assertEquals(Verdict.UNDETERMINED, result.verdict());
    assertEquals("", result.comment());
  }
}
