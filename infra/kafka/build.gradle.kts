plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))        // 도메인 이벤트 → ~Message 매핑이 여기 삶 (S2)
    implementation(project(":application"))   // 발행 포트 구현 — DIP(바깥→안)
    implementation(project(":message"))
    implementation(libs.spring.kafka)
    implementation(libs.spring.jdbc)          // 아웃박스 JdbcClient
    implementation(libs.jackson.databind)     // tools.jackson (Jackson 3) — 기존 카탈로그 항목 재사용
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.api)            // MDC(traceId) + 로그
    testRuntimeOnly(libs.logback.classic)     // 바인딩이 없으면 MDC가 no-op이라 traceId 테스트가 무의미해짐
}
