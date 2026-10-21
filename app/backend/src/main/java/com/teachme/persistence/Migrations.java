package com.teachme.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Flyway の migration を適用する。起動のたびに呼ぶ。適用済みの migration は読み飛ばす。 */
public final class Migrations {
  private Migrations() {}

  public static void migrate(DataSource dataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }
}
