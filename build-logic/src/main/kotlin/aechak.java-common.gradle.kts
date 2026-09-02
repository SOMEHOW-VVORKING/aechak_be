// Java 모듈 공통 베이스 컨벤션 — Java로 작성하는 모듈(admin)이 적용한다.
// kotlin-common과 달리 build-logic classpath에 올릴 외부 플러그인이 없다 — java는 Gradle 내장 플러그인이다.
plugins {
    id("java")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
