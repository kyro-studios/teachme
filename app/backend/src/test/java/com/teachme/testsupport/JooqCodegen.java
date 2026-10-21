package com.teachme.testsupport;

import java.nio.file.Path;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;

/**
 * Testcontainers の PostgreSQL に migration を適用してから、jOOQ のコードを生成する。
 *
 * <p>生成物は `persistence/generated` に出力し、git に入れる (docs/architecture.md の 3 節)。`./gradlew
 * generateJooq` から実行する。
 */
public final class JooqCodegen {
  private static final String PACKAGE_NAME = "com.teachme.persistence.generated";

  private JooqCodegen() {}

  /**
   * @param args 生成先のソースのルート (app/backend/src/main/java)
   */
  public static void main(String[] args) throws Exception {
    Path sourceRoot = Path.of(args[0]);
    PostgresTestDatabase.resetSchema();

    Configuration configuration =
        new Configuration()
            .withJdbc(
                new Jdbc()
                    .withUrl(PostgresTestDatabase.jdbcUrl())
                    .withUser(PostgresTestDatabase.username())
                    .withPassword(PostgresTestDatabase.password()))
            .withGenerator(
                new Generator()
                    .withDatabase(
                        new Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema("public")
                            .withExcludes("flyway_schema_history"))
                    .withTarget(
                        new Target()
                            .withPackageName(PACKAGE_NAME)
                            .withDirectory(sourceRoot.toString())));

    GenerationTool.generate(configuration);
  }
}
