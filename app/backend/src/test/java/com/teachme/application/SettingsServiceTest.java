package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.domain.UserSettings;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private SettingsService service;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    service =
        new SettingsService(
            new Repositories(dataSource).settings(), Fixtures.clockAt("2026-10-21T10:00"));
  }

  private static SettingsCommand command(
      StudentType studentType, Subject subject, String unitName, int lessonsPerWeek) {
    return new SettingsCommand(
        studentType, subject, UnitName.parse(unitName).orElseThrow(), lessonsPerWeek, true);
  }

  @Test
  void 設定を保存すると_完了になり_同じ内容を取得できる() {
    UserSettings saved = service.save(USER, command(StudentType.LOGICAL, Subject.MATH, "二次関数", 3));

    assertEquals(saved, service.find(USER).orElseThrow());
    assertEquals(StudentType.LOGICAL, saved.studentType());
    assertEquals(Subject.MATH, saved.subject());
    assertEquals("二次関数", saved.unitName());
    assertEquals(3, saved.lessonsPerWeek());
  }

  @Test
  void 個人情報の確認が無いと_保存せず_項目の誤りを返す() {
    InvalidInputException error =
        assertThrows(
            InvalidInputException.class,
            () ->
                service.save(
                    USER,
                    new SettingsCommand(
                        StudentType.LOGICAL,
                        Subject.MATH,
                        UnitName.parse("二次関数").orElseThrow(),
                        3,
                        false)));

    assertTrue(error.fields().containsKey("privacyConfirmed"));
    assertTrue(service.find(USER).isEmpty());
  }

  @Test
  void 同じ教科の同名の単元は_作り直さずに使う() {
    UUID first = service.save(USER, command(StudentType.LOGICAL, Subject.MATH, "二次関数", 3)).unitId();
    service.save(USER, command(StudentType.LOGICAL, Subject.MATH, "一次関数", 3));

    UUID again =
        service.save(USER, command(StudentType.LOGICAL, Subject.MATH, " 二次関数 ", 3)).unitId();

    assertEquals(first, again);
  }

  @Test
  void 完了後は担当する生徒を変えられず_ほかの項目は変えられる() {
    service.save(USER, command(StudentType.INTUITIVE, Subject.MATH, "二次関数", 3));

    InvalidInputException error =
        assertThrows(
            InvalidInputException.class,
            () -> service.save(USER, command(StudentType.LOGICAL, Subject.MATH, "二次関数", 3)));
    assertTrue(error.fields().containsKey("studentTypeId"));
    assertEquals(StudentType.INTUITIVE, service.find(USER).orElseThrow().studentType());

    UserSettings changed =
        service.save(USER, command(StudentType.INTUITIVE, Subject.SCIENCE, "力のはたらき", 5));
    assertEquals(Subject.SCIENCE, changed.subject());
    assertEquals("力のはたらき", changed.unitName());
    assertEquals(5, changed.lessonsPerWeek());
  }

  @Test
  void 他の利用者の設定は見えず_設定が無いと_setup_required_になる() {
    service.save(USER, command(StudentType.LOGICAL, Subject.MATH, "二次関数", 3));

    assertTrue(service.find(OTHER).isEmpty());
    ConflictException error =
        assertThrows(ConflictException.class, () -> service.requireCompleted(OTHER));
    assertEquals("setup_required", error.code());
  }
}
