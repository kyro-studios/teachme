package com.teachme.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/** 授業と相談の統合テストの準備。進行中の授業と相談は、開始の機能を使わずに、直接 DB へ入れる (開始の機能は、ほかの段階で作る)。 */
public final class DialogueFixtures {

  private DialogueFixtures() {}

  /** 教材を 1 件入れる。整理の結果は入れない。 */
  public static UUID insertMaterial(DataSource dataSource, UUID unitId) {
    UUID id = UUID.randomUUID();
    execute(
        dataSource,
        "insert into materials (id, unit_id, status) values (?, ?, 'organizing')",
        id,
        unitId);
    return id;
  }

  /**
   * 進行中の授業を 1 件入れる。開始から 600 秒の期限は、`deadlineAt` から決める。
   *
   * @param roundCount 往復の数
   */
  public static UUID insertLesson(
      DataSource dataSource, UUID userId, UUID materialId, Instant deadlineAt, int roundCount) {
    UUID id = UUID.randomUUID();
    execute(
        dataSource,
        "insert into lessons (id, user_id, unit_id, material_id, started_at, deadline_at,"
            + " round_count) select ?::uuid, ?::uuid, unit_id, id, ?::timestamptz,"
            + " ?::timestamptz, ?::smallint from materials where id = ?::uuid",
        id,
        userId,
        timestamp(deadlineAt.minusSeconds(600)),
        timestamp(deadlineAt),
        (short) roundCount,
        materialId);
    return id;
  }

  /**
   * 進行中の相談を 1 件入れる。
   *
   * @param roundCount 往復の数
   */
  public static UUID insertConsultation(
      DataSource dataSource, UUID userId, UUID materialId, Instant deadlineAt, int roundCount) {
    UUID id = UUID.randomUUID();
    execute(
        dataSource,
        "insert into consultations (id, user_id, material_id, started_at, deadline_at,"
            + " round_count) values (?::uuid, ?::uuid, ?::uuid, ?::timestamptz,"
            + " ?::timestamptz, ?::smallint)",
        id,
        userId,
        materialId,
        timestamp(deadlineAt.minusSeconds(600)),
        timestamp(deadlineAt),
        (short) roundCount);
    return id;
  }

  /** 字幕の件数。`parentId` は授業か相談の識別子。 */
  public static int countTurns(DataSource dataSource, UUID parentId) {
    return queryInt(
        dataSource,
        "select count(*) from dialogue_turns where lesson_id = ?::uuid"
            + " or consultation_id = ?::uuid",
        parentId,
        parentId);
  }

  /** 未応答の利用者の字幕の件数。 */
  public static int countUnrepliedUserTurns(DataSource dataSource, UUID parentId) {
    return queryInt(
        dataSource,
        "select count(*) from dialogue_turns where (lesson_id = ?::uuid"
            + " or consultation_id = ?::uuid) and speaker = 'user' and replied = false",
        parentId,
        parentId);
  }

  /** 授業の往復の数。 */
  public static int lessonRoundCount(DataSource dataSource, UUID lessonId) {
    return queryInt(dataSource, "select round_count from lessons where id = ?::uuid", lessonId);
  }

  /** 授業の度合い (3 件)。 */
  public static List<Integer> lessonDegrees(DataSource dataSource, UUID lessonId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("select degrees from lessons where id = ?::uuid")) {
      statement.setObject(1, lessonId);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        Object[] values = (Object[]) rows.getArray(1).getArray();
        List<Integer> degrees = new ArrayList<>();
        for (Object value : values) {
          degrees.add(((Number) value).intValue());
        }
        return degrees;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("テストの確認の SQL が失敗しました", e);
    }
  }

  /** 指定した話し手の字幕の識別子 (最初の 1 件)。 */
  public static UUID turnIdOf(DataSource dataSource, UUID parentId, String speaker) {
    return queryUuid(
        dataSource,
        "select id from dialogue_turns where (lesson_id = ?::uuid or consultation_id = ?::uuid)"
            + " and speaker = ? order by seq limit 1",
        parentId,
        parentId,
        speaker);
  }

  /** 授業を直接消す。中断された後の確かめに使う (中断の機能は、ほかの段階で作る)。 */
  public static void deleteLesson(DataSource dataSource, UUID lessonId) {
    execute(dataSource, "delete from lessons where id = ?::uuid", lessonId);
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static int queryInt(DataSource dataSource, String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("テストの確認の SQL が失敗しました", e);
    }
  }

  private static UUID queryUuid(DataSource dataSource, String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getObject(1, UUID.class);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("テストの確認の SQL が失敗しました", e);
    }
  }

  private static void execute(DataSource dataSource, String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("テストの準備の SQL が失敗しました", e);
    }
  }

  private static void bind(PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      statement.setObject(i + 1, params[i]);
    }
  }
}
