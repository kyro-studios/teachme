package com.teachme.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers の PostgreSQL に、Flyway の migration を適用する、統合テストの共通の土台。
 *
 * <p>コンテナは JVM の中で 1 つだけ起動する。テストのたびに {@link #resetSchema()} で DB を空に戻してから、migration を適用し直す。本番の DB
 * には接続しない。
 */
public final class PostgresTestDatabase {
  /** 本番と同じメジャーバージョン (scripts/start-app.sh と合わせる)。 */
  private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE);

  private PostgresTestDatabase() {}

  /** DB を空に戻し、migration を適用する。 */
  public static synchronized void resetSchema() {
    start();
    Flyway flyway =
        Flyway.configure()
            .dataSource(jdbcUrl(), username(), password())
            .locations("classpath:db/migration")
            // Flyway 10 以降は clean が既定で無効のため、テストの DB を空に戻すために有効にする
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();
  }

  /** テスト用の DB に、新しい接続を開く。閉じるのは呼び出し側。 */
  public static Connection connect() throws SQLException {
    start();
    return DriverManager.getConnection(jdbcUrl(), username(), password());
  }

  public static synchronized String jdbcUrl() {
    start();
    return POSTGRES.getJdbcUrl();
  }

  public static synchronized String username() {
    start();
    return POSTGRES.getUsername();
  }

  public static synchronized String password() {
    start();
    return POSTGRES.getPassword();
  }

  private static synchronized void start() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
  }
}
