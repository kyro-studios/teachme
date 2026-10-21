package com.teachme.testsupport;

import com.teachme.persistence.DataSources;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;

/** 統合テストの共通の準備。DB は Testcontainers の PostgreSQL を使う。 */
public final class Fixtures {

  public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

  private Fixtures() {}

  /** DB を空に戻して migration を適用し、その DB に向けた DataSource を返す。 */
  public static DataSource freshDataSource() {
    PostgresTestDatabase.resetSchema();
    return DataSources.postgres(
        PostgresTestDatabase.jdbcUrl(),
        PostgresTestDatabase.username(),
        PostgresTestDatabase.password());
  }

  /**
   * 日本時間の、指定した日時の時計。
   *
   * @param localDateTime `2026-10-21T10:00` の形
   */
  public static Clock clockAt(String localDateTime) {
    return Clock.fixed(LocalDateTime.parse(localDateTime).atZone(JAPAN).toInstant(), JAPAN);
  }

  /** 授業の記録を直接入れる (授業の機能は、このタスクには無いため)。 */
  public static void insertLessonRecord(
      DataSource dataSource, UUID userId, UUID unitId, Instant completedAt) {
    execute(
        dataSource,
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text) values (?, ?, ?, ?, ?, '{1,1,1}', 'まとめ')",
        UUID.randomUUID(),
        userId,
        unitId,
        UUID.randomUUID(),
        OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC));
  }

  /** 単元の、前回の模試からの授業の完了数を決める。 */
  public static void setLessonsSinceExam(DataSource dataSource, UUID unitId, int count) {
    execute(
        dataSource, "update units set lessons_since_exam = ? where id = ?", (short) count, unitId);
  }

  private static void execute(DataSource dataSource, String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("テストの準備の SQL が失敗しました", e);
    }
  }
}
