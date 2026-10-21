package com.teachme.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.testsupport.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** migration (V1) の制約を、PostgreSQL に直接入れて確かめる。違反は、制約の名前がエラーに含まれることで判定する。 */
class SchemaConstraintsTest {
  private Connection connection;

  @BeforeEach
  void setUp() throws SQLException {
    PostgresTestDatabase.resetSchema();
    connection = PostgresTestDatabase.connect();
  }

  @AfterEach
  void tearDown() throws SQLException {
    connection.close();
  }

  @Test
  void dialogue_turns_は授業と相談の両方を親にできない() throws SQLException {
    UUID userId = insertUser();
    UUID lessonId = insertLesson(userId, insertMaterial(insertUnit(userId)));
    UUID consultationId = insertConsultation(userId, insertMaterial(insertUnit(userId)));

    assertViolation(
        "dialogue_turns_one_parent",
        "insert into dialogue_turns (id, lesson_id, consultation_id, seq, speaker, body)"
            + " values (?, ?, ?, 1, 'user', 'x')",
        UUID.randomUUID(),
        lessonId,
        consultationId);
  }

  @Test
  void dialogue_turns_は親を持たなければ入らない() {
    assertViolation(
        "dialogue_turns_one_parent",
        "insert into dialogue_turns (id, lesson_id, consultation_id, seq, speaker, body)"
            + " values (?, null, null, 1, 'user', 'x')",
        UUID.randomUUID());
  }

  @Test
  void dialogue_turns_は授業か相談の一方を親にすれば入る() throws SQLException {
    UUID userId = insertUser();
    UUID lessonId = insertLesson(userId, insertMaterial(insertUnit(userId)));
    UUID consultationId = insertConsultation(userId, insertMaterial(insertUnit(userId)));

    execute(
        "insert into dialogue_turns (id, lesson_id, consultation_id, seq, speaker, body)"
            + " values (?, ?, null, 1, 'user', 'x')",
        UUID.randomUUID(),
        lessonId);
    execute(
        "insert into dialogue_turns (id, lesson_id, consultation_id, seq, speaker, body)"
            + " values (?, null, ?, 1, 'user', 'x')",
        UUID.randomUUID(),
        consultationId);

    assertEquals("2", queryString("select count(*) from dialogue_turns"));
  }

  @Test
  void 未応答の利用者の字幕は_授業ごとに_1_件まで() throws SQLException {
    UUID userId = insertUser();
    UUID lessonId = insertLesson(userId, insertMaterial(insertUnit(userId)));

    execute(
        "insert into dialogue_turns (id, lesson_id, seq, speaker, body, replied)"
            + " values (?, ?, 1, 'user', 'a', false)",
        UUID.randomUUID(),
        lessonId);
    execute(
        "insert into dialogue_turns (id, lesson_id, seq, speaker, body, replied)"
            + " values (?, ?, 2, 'user', 'b', true)",
        UUID.randomUUID(),
        lessonId);

    assertViolation(
        "dialogue_turns_unreplied_user_per_lesson",
        "insert into dialogue_turns (id, lesson_id, seq, speaker, body, replied)"
            + " values (?, ?, 3, 'user', 'c', false)",
        UUID.randomUUID(),
        lessonId);
  }

  @Test
  void user_settings_は個人情報の確認が_true_のものだけ入る() throws SQLException {
    UUID userId = insertUser();
    UUID unitId = insertUnit(userId);

    assertViolation(
        "user_settings_privacy_confirmed_true",
        "insert into user_settings (user_id, student_type, current_unit_id, lessons_per_week,"
            + " privacy_confirmed) values (?, 'intuitive', ?, 3, false)",
        userId,
        unitId);

    execute(
        "insert into user_settings (user_id, student_type, current_unit_id, lessons_per_week,"
            + " privacy_confirmed) values (?, 'intuitive', ?, 3, true)",
        userId,
        unitId);
    assertEquals("1", queryString("select count(*) from user_settings"));
  }

  @Test
  void units_は教科と単元名の範囲を守り_同じ単元名を重複させない() throws SQLException {
    UUID userId = insertUser();
    execute(
        "insert into units (id, user_id, subject, name) values (?, ?, 'math', '二次関数')",
        UUID.randomUUID(),
        userId);

    assertViolation(
        "units_subject_check",
        "insert into units (id, user_id, subject, name) values (?, ?, 'english', 'x')",
        UUID.randomUUID(),
        userId);
    assertViolation(
        "units_name_check",
        "insert into units (id, user_id, subject, name) values (?, ?, 'math', ?)",
        UUID.randomUUID(),
        userId,
        "あ".repeat(31));
    assertViolation(
        "units_user_id_subject_name_key",
        "insert into units (id, user_id, subject, name) values (?, ?, 'math', '二次関数')",
        UUID.randomUUID(),
        userId);
  }

  @Test
  void 教材の整理の結果は_organized_のときだけ入り_organized_では必ず入る() throws SQLException {
    UUID unitId = insertUnit(insertUser());

    assertViolation(
        "materials_result_only_when_organized",
        "insert into materials (id, unit_id, status, problem_text) values (?, ?, 'organizing', 'x')",
        UUID.randomUUID(),
        unitId);
    assertViolation(
        "materials_result_required_when_organized",
        "insert into materials (id, unit_id, status) values (?, ?, 'organized')",
        UUID.randomUUID(),
        unitId);

    execute(
        "insert into materials (id, unit_id, status, problem_text, figures, model_answer,"
            + " explanation_steps, student_work, teaching_points) values (?, ?, 'organized',"
            + " 'x', '[]'::jsonb, 'y', '[\"step\"]'::jsonb, 'z', '[]'::jsonb)",
        UUID.randomUUID(),
        unitId);
    assertEquals("1", queryString("select count(*) from materials"));
  }

  @Test
  void 教材の画像は_位置が_1_から_3_で_教材ごとに一意() throws SQLException {
    UUID materialId = insertMaterial(insertUnit(insertUser()));

    assertViolation(
        "material_images_position_check",
        "insert into material_images (id, material_id, position, content_type, data)"
            + " values (?, ?, 4, 'image/png', '\\x00')",
        UUID.randomUUID(),
        materialId);

    execute(
        "insert into material_images (id, material_id, position, content_type, data)"
            + " values (?, ?, 1, 'image/png', '\\x00')",
        UUID.randomUUID(),
        materialId);
    assertViolation(
        "material_images_material_id_position_key",
        "insert into material_images (id, material_id, position, content_type, data)"
            + " values (?, ?, 1, 'image/png', '\\x00')",
        UUID.randomUUID(),
        materialId);
  }

  @Test
  void 授業の度合いは_3_件で_各_0_から_2_の値を持ち_初期値は_0_が_3_件() throws SQLException {
    UUID userId = insertUser();
    UUID unitId = insertUnit(userId);
    UUID materialId = insertMaterial(unitId);

    assertViolation(
        "lessons_degrees_check",
        "insert into lessons (id, user_id, unit_id, material_id, started_at, deadline_at, degrees)"
            + " values (?, ?, ?, ?, now(), now(), '{0,0,3}')",
        UUID.randomUUID(),
        userId,
        unitId,
        materialId);
    assertViolation(
        "lesson_records_degrees_check",
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text) values (?, ?, ?, ?, now(), '{0,1}', 'x')",
        UUID.randomUUID(),
        userId,
        unitId,
        materialId);

    UUID lessonId = insertLesson(userId, materialId);
    assertEquals(
        "{0,0,0}", queryString("select degrees::text from lessons where id = ?", lessonId));
  }

  @Test
  void 模試の得点は_0_から_100() throws SQLException {
    UUID unitId = insertUnit(insertUser());

    assertViolation(
        "mock_exams_score_check",
        "insert into mock_exams (id, unit_id, taken_at, score, deviation, problem_text,"
            + " answer_sheet, student_comment) values (?, ?, now(), 101, 50.0, 'a', 'b', 'c')",
        UUID.randomUUID(),
        unitId);
  }

  @Test
  void 教材を削除すると_授業と相談と字幕も消える() throws SQLException {
    UUID userId = insertUser();
    UUID materialId = insertMaterial(insertUnit(userId));
    UUID lessonId = insertLesson(userId, materialId);
    UUID consultationId = insertConsultation(userId, materialId);
    execute(
        "insert into dialogue_turns (id, lesson_id, seq, speaker, body) values (?, ?, 1, 'user', 'x')",
        UUID.randomUUID(),
        lessonId);
    execute(
        "insert into dialogue_turns (id, consultation_id, seq, speaker, body)"
            + " values (?, ?, 1, 'user', 'x')",
        UUID.randomUUID(),
        consultationId);

    execute("delete from materials where id = ?", materialId);

    assertEquals("0", queryString("select count(*) from lessons"));
    assertEquals("0", queryString("select count(*) from consultations"));
    assertEquals("0", queryString("select count(*) from dialogue_turns"));
  }

  @Test
  void 単元を削除すると_教材と授業の記録と模試の結果も消える() throws SQLException {
    UUID userId = insertUser();
    UUID unitId = insertUnit(userId);
    UUID materialId = insertMaterial(unitId);
    execute(
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text) values (?, ?, ?, ?, now(), '{0,0,0}', 'x')",
        UUID.randomUUID(),
        userId,
        unitId,
        materialId);
    execute(
        "insert into mock_exams (id, unit_id, taken_at, score, deviation, problem_text,"
            + " answer_sheet, student_comment) values (?, ?, now(), 50, 50.0, 'a', 'b', 'c')",
        UUID.randomUUID(),
        unitId);

    execute("delete from units where id = ?", unitId);

    assertEquals("0", queryString("select count(*) from materials"));
    assertEquals("0", queryString("select count(*) from lesson_records"));
    assertEquals("0", queryString("select count(*) from mock_exams"));
  }

  private UUID insertUser() throws SQLException {
    UUID id = UUID.randomUUID();
    execute("insert into users (id) values (?)", id);
    return id;
  }

  /** 単元名は、同じユーザーの中で重複しないように、ランダムな文字列にする。 */
  private UUID insertUnit(UUID userId) throws SQLException {
    UUID id = UUID.randomUUID();
    String name = "単元" + id.toString().substring(0, 8);
    execute(
        "insert into units (id, user_id, subject, name) values (?, ?, 'math', ?)",
        id,
        userId,
        name);
    return id;
  }

  private UUID insertMaterial(UUID unitId) throws SQLException {
    UUID id = UUID.randomUUID();
    execute("insert into materials (id, unit_id, status) values (?, ?, 'organizing')", id, unitId);
    return id;
  }

  /** 授業の単元は、教材の単元 (materials.unit_id) に合わせる。 */
  private UUID insertLesson(UUID userId, UUID materialId) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "insert into lessons (id, user_id, unit_id, material_id, started_at, deadline_at)"
            + " select ?::uuid, ?::uuid, unit_id, id, now(), now() + interval '10 minutes'"
            + " from materials where id = ?",
        id,
        userId,
        materialId);
    return id;
  }

  private UUID insertConsultation(UUID userId, UUID materialId) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "insert into consultations (id, user_id, material_id, started_at, deadline_at)"
            + " values (?, ?, ?, now(), now() + interval '10 minutes')",
        id,
        userId,
        materialId);
    return id;
  }

  /** 制約に違反する SQL が失敗し、エラーに制約の名前が含まれることを確かめる。 */
  private void assertViolation(String constraintName, String sql, Object... params) {
    SQLException error = assertThrows(SQLException.class, () -> execute(sql, params));
    assertTrue(
        error.getMessage().contains(constraintName),
        () -> "制約 " + constraintName + " の違反ではありません: " + error.getMessage());
  }

  private void execute(String sql, Object... params) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      statement.executeUpdate();
    }
  }

  /** 1 行 1 列の問い合わせの値を、文字列で返す。 */
  private String queryString(String sql, Object... params) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getString(1);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      statement.setObject(i + 1, params[i]);
    }
  }
}
