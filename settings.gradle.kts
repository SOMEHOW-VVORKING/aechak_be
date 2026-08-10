pluginManagement {
    includeBuild("build-logic")              // 컨벤션 플러그인 — buildSrc 대신 includeBuild 방식
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"   // JDK 21 툴체인 자동 프로비저닝
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    // gradle/libs.versions.toml은 관례 경로라 자동 인식된다
}

rootProject.name = "aechak"

// 새 모듈은 여기에 등록한다.
// boot/(실행 모듈 그룹)와 infra/{persistence,client,kafka,redis}(기술 분류 그룹)는 모듈이 아니라 폴더다 —
// "boot:api"처럼 계층 이름으로 include하면 빈 중간 프로젝트가 생기므로,
// 모듈 이름은 평평하게 두고 아래 projectDir로 위치만 매핑한다.
// infra 구체 모듈 예: persistence/jpa = :jpa-persistence, client/pg-client = :pg-client.
include(
    "common",
    "web-common",
    "web-security",
    "domain",
    "application",
    "message",                        // Kafka 통합 메시지 계약 (00-overview A-2)
    "api",
    "batch",                          // admin — MVP 제외, 필요 시점에 생성
    "jpa-persistence",
    "pg-client",          // kafka는 어댑터 코드가 생길 때 하위 모듈 추가
    "social-client",
    "redis",                // 소셜 로그인(ACC-01): id_token 검증 / refresh token 저장
    "s3-client",                             // 오브젝트 스토리지 어댑터
    "sms-client",                            // SMS 발송 어댑터(전화 인증)
    "kafka",
)

project(":api").projectDir = file("boot/api")
project(":batch").projectDir = file("boot/batch")
project(":jpa-persistence").projectDir = file("infra/persistence/jpa")
project(":pg-client").projectDir = file("infra/client/pg-client")
project(":social-client").projectDir = file("infra/client/social-client")
project(":s3-client").projectDir = file("infra/client/s3-client")
project(":sms-client").projectDir = file("infra/client/sms-client")
project(":redis").projectDir = file("infra/redis")
project(":kafka").projectDir = file("infra/kafka")
