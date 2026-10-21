plugins {
    java
    application
    id("com.diffplug.spotless") version "8.10.4"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("org.jooq:jooq:3.20.5")
    implementation("org.flywaydb:flyway-core:11.10.5")
    implementation("org.flywaydb:flyway-database-postgresql:11.10.5")
    runtimeOnly("org.postgresql:postgresql:42.7.7")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.javalin:javalin-testtools:6.7.0")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    // jOOQ のコード生成 (generateJooq) は、テストのソースの JooqCodegen が行う
    testImplementation("org.jooq:jooq-codegen:3.20.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.teachme.bootstrap.Main"
}

// Testcontainers の PostgreSQL に migration を適用してから、jOOQ のコードを生成する。
// 生成物は persistence/generated に出力し、git に入れる。Docker が必要。
tasks.register<JavaExec>("generateJooq") {
    group = "jooq"
    description = "Testcontainers の PostgreSQL に migration を適用し、jOOQ のコードを生成する"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.teachme.testsupport.JooqCodegen"
    args(file("src/main/java").absolutePath)
}

tasks.withType<JavaCompile> {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat()
        target("src/**/*.java")
        targetExclude("src/main/java/com/teachme/persistence/generated/**")
    }
}
