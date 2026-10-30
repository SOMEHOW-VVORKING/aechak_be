// 전 모듈 공통 베이스 컨벤션. 모든 모듈은 컨벤션 id 하나 + 자기 의존성 목록만 갖는다.
// 컨벤션 수정은 전 모듈에 영향을 주므로 반드시 리뷰를 거친다. 여기에 의존성을 추가하지 않는다
// (예외는 jpa-entity의 persistence-api, spring-library의 BOM 두 곳뿐).
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("org.jetbrains.kotlin.jvm")                    // 버전 없음 — build-logic classpath가 결정
    id("org.jlleitschuh.gradle.ktlint")               // ktlint 기반 린트/포맷
    id("org.gradle.test-retry")                       // CI에서 실패한 테스트만 재시도 — 아래 Test 블록이 설정
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // 테스트 JVM 힙. 기본값으로는 CI 에서 OutOfMemoryError 가 난다 — 스프링 테스트 컨텍스트가
    // 여럿 캐시되고 그 각각이 임베디드 카프카를 들고 있어서, 한 JVM 에 브로커 셋이 동시에 산다.
    // 러너(ubuntu-latest)의 물리 메모리 안에서 넉넉한 값으로 둔다.
    maxHeapSize = "2g"

    // 실패 원인을 CI 로그 자체에 남긴다.
    // 기본 출력은 "AssertionFailedError at ReviewCreateIntegrationTest.kt:283" 한 줄뿐이어서
    // 기대값·실제값·스택이 없다. 그러면 로그에서 눈에 띄는 다른 ERROR 를 원인으로 오진한다 —
    // run#588 이 실제로 그렇게 읽혔다(실패 69초 뒤 종료 훅의 Kafka 잡음을 원인으로 지목).
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    // 플레이크 한 건이 전체 머지를 막는 것을 푼다 — 단 '숨기지 않는' 조건에서만.
    // 실패한 테스트만 다시 돌리므로 10분짜리 전체 재실행이 필요 없다.
    // failOnPassedAfterRetry=false 라 머지는 풀리지만, 재시도로 통과한 테스트는 JUnit XML 에
    // flaky 로 남고 ci.yml 의 요약 단계가 이름까지 잡 요약에 올린다 — 조용히 넘어가지 않는다.
    retry {
        // 로컬은 0회. 개발자 앞에서는 실패가 실패로 보여야 한다.
        maxRetries.set(providers.environmentVariable("CI").map { 1 }.orElse(0))
        // 한 번에 여러 테스트가 깨지면 플레이크가 아니라 진짜 회귀다 — 그때는 재시도를 아예 끈다
        maxFailures.set(5)
        failOnPassedAfterRetry.set(false)
    }
}

dependencies {
    "testImplementation"(kotlin("test"))              // 카탈로그 불필요 — 버전이 kotlin 플러그인을 따라감
}
