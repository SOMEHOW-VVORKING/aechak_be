// Java 실행 모듈(admin) 컨벤션: bootJar/bootRun을 제공한다 — kotlin 체인(aechak.spring-boot-app)의 Java 병렬판.
// plugin.spring(allopen)에 해당하는 것이 없는 이유: Java 클래스는 기본 non-final이라 프록시 생성에 조치가 불필요하다.
plugins {
    id("aechak.java-common")
    id("org.springframework.boot")
}

// precompiled script 안에서는 libs.* 타입세이프 접근자가 생성되지 않아 카탈로그 API로 우회한다.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    // Spring BOM을 platform으로 적용 — 카탈로그의 버전 없는 spring 항목들이 여기서 해석된다
    "implementation"(platform(libs.findLibrary("spring-boot-bom").get()))
    "testImplementation"(platform(libs.findLibrary("spring-boot-bom").get()))
    // annotation processor 경로에도 BOM 적용 — Lombok처럼 버전 없는 프로세서가 여기서 해석된다
    "annotationProcessor"(platform(libs.findLibrary("spring-boot-bom").get()))
    "testAnnotationProcessor"(platform(libs.findLibrary("spring-boot-bom").get()))
}
