package com.teachme.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.teachme.testsupport.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationTest {
  @Test
  void 空の_DB_に_migration_を適用すると_アーキテクチャ設計_7_節の表が揃う() throws SQLException {
    PostgresTestDatabase.resetSchema();

    Set<String> tables = new HashSet<>();
    try (Connection connection = PostgresTestDatabase.connect();
        Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "select table_name from information_schema.tables"
                    + " where table_schema = 'public' and table_type = 'BASE TABLE'")) {
      while (rs.next()) {
        tables.add(rs.getString(1));
      }
    }
    tables.remove("flyway_schema_history");

    assertEquals(
        Set.of(
            "users",
            "user_settings",
            "units",
            "today_modes",
            "materials",
            "material_images",
            "lessons",
            "consultations",
            "dialogue_turns",
            "lesson_records",
            "mock_exams"),
        tables);
  }
}
