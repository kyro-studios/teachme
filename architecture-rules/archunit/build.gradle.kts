plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile> {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    // 検査するクラスファイルのディレクトリ。app/ のビルドの出力を、パスで指定して読み込む
    val classesDir = providers.environmentVariable("ARCH_CLASSES_DIR")
    inputs.property("classesDir", classesDir.orElse(""))
    doFirst {
        systemProperty("archunit.classesDir", classesDir.orNull ?: error("ARCH_CLASSES_DIR を指定してください"))
    }
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true }
}
