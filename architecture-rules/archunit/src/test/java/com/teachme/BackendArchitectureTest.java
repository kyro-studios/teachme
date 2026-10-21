package com.teachme;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** バックエンドのモジュール間の依存のルール (docs/architecture.md の 4.1)。 */
class BackendArchitectureTest {
  private static final String ROOT = "com.teachme";

  /** パッケージ名 (ROOT からの相対)。`ai` は、サブパッケージを含まない。 */
  private static final List<String> PACKAGES =
      List.of("domain", "ai", "ai.real", "ai.fake", "persistence", "application", "web", "bootstrap");

  /** 各パッケージが依存してよい、ほかのパッケージ (全体の構成の 4.1 の図)。 */
  private static final Map<String, Set<String>> ALLOWED =
      Map.of(
          "domain", Set.of(),
          "ai", Set.of("domain"),
          "ai.real", Set.of("ai", "domain"),
          "ai.fake", Set.of("ai", "domain"),
          "persistence", Set.of("domain"),
          "application", Set.of("domain", "ai", "persistence"),
          "web", Set.of("application", "domain"),
          "bootstrap", Set.copyOf(PACKAGES));

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPath(Path.of(System.getProperty("archunit.classesDir")));
  }

  private static String pkg(String name) {
    return ROOT + "." + name;
  }

  @Test
  void パッケージの依存は全体の構成の向きだけに限る() {
    for (String from : PACKAGES) {
      for (String to : PACKAGES) {
        if (from.equals(to) || ALLOWED.get(from).contains(to)) {
          continue;
        }
        noClasses()
            .that()
            .resideInAPackage(pkg(from))
            .should()
            .dependOnClassesThat()
            .resideInAPackage(pkg(to))
            .allowEmptyShould(true)
            .because(from + " は " + to + " に依存してはいけない")
            .check(classes);
      }
    }
  }

  @Test
  void domain_は外部のライブラリに依存しない() {
    noClasses()
        .that()
        .resideInAPackage(pkg("domain"))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.javalin..", "org.jooq..", "com.fasterxml.jackson..", "java.net.http..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void jooq_を使うのは_persistence_だけ() {
    noClasses()
        .that()
        .resideOutsideOfPackage(pkg("persistence") + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.jooq..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void javalin_を使うのは_web_と_bootstrap_だけ() {
    noClasses()
        .that()
        .resideOutsideOfPackages(pkg("web") + "..", pkg("bootstrap") + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.javalin..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void パッケージの循環を作らない() {
    ArchRule rule =
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
    rule.check(classes);
  }
}
