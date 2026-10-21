package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.teachme.testsupport.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ComponentsTest {
  @Test
  void 時計は日本時間で_起動のたびに移行を適用しても_失敗しない() throws SQLException {
    PostgresTestDatabase.resetSchema();
    DatabaseConfig config =
        new DatabaseConfig(
            PostgresTestDatabase.jdbcUrl(),
            PostgresTestDatabase.username(),
            PostgresTestDatabase.password());

    Components first = Components.assemble(config);
    Components restarted = Components.assemble(config);

    assertEquals(ZoneId.of("Asia/Tokyo"), first.clock().getZone());
    assertEquals(ZoneId.of("Asia/Tokyo"), restarted.clock().getZone());
    try (Connection connection = restarted.dataSource().getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "select count(*) from flyway_schema_history where success and version = '1'")) {
      rs.next();
      assertEquals(1, rs.getInt(1));
    }
  }
}
