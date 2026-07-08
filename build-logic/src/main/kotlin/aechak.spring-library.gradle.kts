// Spring 라이브러리 모듈(web-common / application / infra:*) 공용 컨벤션.
plugins {
    id("aechak.kotlin-common")
    id("org.jetbrains.kotlin.plugin.spring")          // @Component/@Service 등 스프링 빈 클래스 자동 open
}

// precompiled script 안에서는 libs.* 타입세이프 접근자가 생성되지 않아 카탈로그 API로 우회한다.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    // Spring BOM을 platform으로 적용 — 카탈로그의 버전 없는 spring 항목들이 여기서 해석된다
    "implementation"(platform(libs.findLibrary("spring-boot-bom").get()))
    "testImplementation"(platform(libs.findLibrary("spring-boot-bom").get()))
}
