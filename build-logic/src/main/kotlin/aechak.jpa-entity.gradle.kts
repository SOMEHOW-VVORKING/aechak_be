// domain 모듈 전용 컨벤션: JPA 엔티티가 요구하는 no-arg 생성자·open 클래스 처리를 제공한다.
plugins {
    id("aechak.kotlin-common")
    id("org.jetbrains.kotlin.plugin.jpa")             // @Entity 계열 no-arg 생성자 자동 생성
    id("org.jetbrains.kotlin.plugin.allopen")         // plugin.jpa는 open 처리를 안 해준다 — lazy 프록시용으로 별도 적용
    id("com.google.devtools.ksp")                     // QueryDSL Q클래스 생성 — 엔티티 소스가 있는 이 모듈에서만 실행
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// precompiled script 안에서는 libs.* 타입세이프 접근자가 생성되지 않아 카탈로그 API로 우회한다.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    // 이 컨벤션의 정의 자체가 "persistence-api 위의 엔티티 모듈"이므로 여기서 제공한다.
    // api인 이유: 소비 모듈이 도메인 클래스의 어노테이션 타입을 볼 수 있어야 한다.
    "api"(libs.findLibrary("jakarta-persistence").get())
    // Q클래스 생성(KSP) + 생성된 Q클래스가 참조하는 com.querydsl.core.types.* 컴파일용.
    // 생성물은 domain jar에 포함되어 infra 어댑터가 참조한다.
    "ksp"(libs.findLibrary("querydsl-ksp-codegen").get())
    "implementation"(libs.findLibrary("querydsl-jpa").get())
}
