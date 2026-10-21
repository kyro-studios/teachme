package com.teachme.bootstrap;

import com.teachme.persistence.DataSources;
import com.teachme.persistence.Migrations;
import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;

/** 起動のときに組み立てる、アプリケーションの部品。API の Routes に渡す。 */
public record Components(Clock clock, DataSource dataSource) {

  /** 日付と週は日本時間で数える (docs/architecture.md の 5 節)。 */
  public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

  /** DB に接続し、起動のたびに Flyway の migration を適用してから、部品を組み立てる。 */
  public static Components assemble(DatabaseConfig database) {
    DataSource dataSource =
        DataSources.postgres(database.url(), database.user(), database.password());
    Migrations.migrate(dataSource);
    return new Components(Clock.system(JAPAN), dataSource);
  }
}
