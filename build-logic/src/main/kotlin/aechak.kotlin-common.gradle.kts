// 전 모듈 공통 베이스 컨벤션. 모든 모듈은 컨벤션 id 하나 + 자기 의존성 목록만 갖는다.
// 컨벤션 수정은 전 모듈에 영향을 주므로 반드시 리뷰를 거친다. 여기에 의존성을 추가하지 않는다
// (예외는 jpa-entity의 persistence-api, spring-library의 BOM 두 곳뿐).
plugins {
    id("org.jetbrains.kotlin.jvm")                    // 버전 없음 — build-logic classpath가 결정
    id("org.jlleitschuh.gradle.ktlint")               // ktlint 기반 린트/포맷
}

ktlint {
    // 래퍼 기본값과 무관하게 코어 버전을 고정한다 — Kotlin 2.2 문법 파싱에 1.7.0+ 필수
    version.set(
        extensions.getByType<VersionCatalogsExtension>()
            .named("libs").findVersion("ktlint").get().requiredVersion,
    )
    // 생성 코드(KSP의 Q클래스 등)는 우리 스타일 규칙 대상이 아니다 — build/ 산출물 제외
    filter {
        exclude { it.file.path.contains("${layout.buildDirectory.get().asFile.path}/generated/") }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")   // Boot 4 + Kotlin 2.2 권장 — 어노테이션 타겟 경고 방지
        freeCompilerArgs.add("-Xjsr305=strict")                              // JSR-305 널러빌리티를 쓰는 서드파티 라이브러리 대응
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    "testImplementation"(kotlin("test"))              // 카탈로그 불필요 — 버전이 kotlin 플러그인을 따라감
}
