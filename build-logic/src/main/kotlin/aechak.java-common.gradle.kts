// Java 모듈 공통 베이스 컨벤션 — Java로 작성하는 모듈(admin)이 적용한다.
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("java")                                        // Gradle 내장 플러그인
    id("com.diffplug.spotless")                       // 자바 포맷 검사·수정(spotlessCheck/Apply) — kotlin-common의 ktlint에 대응
    id("org.gradle.test-retry")                       // CI에서 실패한 테스트만 재시도 — 아래 Test 블록이 설정
}

spotless {
    java {
        // 포매터 코어 버전을 카탈로그로 고정한다 — Spotless 번들 기본값에 끌려가지 않게 (ktlint와 같은 방식)
        palantirJavaFormat(
            extensions.getByType<VersionCatalogsExtension>()
                .named("libs").findVersion("palantir-java-format").get().requiredVersion,
        )
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // 테스트 JVM 힙. 기본값으로는 CI 에서 OutOfMemoryError 가 난다 — 스프링 테스트 컨텍스트가
    // 여럿 캐시되고 그 각각이 임베디드 카프카를 들고 있어서, 한 JVM 에 브로커 셋이 동시에 산다.
    // 러너(ubuntu-latest)의 물리 메모리 안에서 넉넉한 값으로 둔다.
    maxHeapSize = "2g"

    // 실패 원인을 CI 로그 자체에 남긴다 — 근거는 kotlin-common 의 같은 블록 주석 참고.
    // run#562 의 AdminNoticeIntegrationTest 실패도 "AssertionFailedError at ...java:305" 한 줄이었다.
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    // 실패한 테스트만 CI 에서 1회 재시도. 재시도로 통과한 것은 숨기지 않고 잡 요약에 올린다.
    retry {
        maxRetries.set(providers.environmentVariable("CI").map { 1 }.orElse(0))
        maxFailures.set(5)
        failOnPassedAfterRetry.set(false)
    }
}
