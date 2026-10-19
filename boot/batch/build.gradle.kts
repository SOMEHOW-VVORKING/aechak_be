// 배치 실행 모듈이자 조립 지점. 웹 응답 규격이 필요 없으므로 web-common을 의존하지 않는다.
plugins { id("aechak.spring-boot-app") }
dependencies {
    implementation(project(":application"))
    implementation(project(":jpa-persistence"))         // 도메인 포트의 구현체 조립
    implementation(project(":kafka"))                   // 아웃박스 스위퍼 조립
    implementation(project(":pii"))                     // PII 암호화 엔진·키 조립 — 주문 파사드가 경유
    implementation(project(":pg-client"))               // 포트원 어댑터·대사 소스
    implementation(project(":s3-client"))               // FileStorage 어댑터 — 장바구니 파사드가 요구
    implementation(kotlin("reflect"))                   // Spring(Data)의 Kotlin 리플렉션 지원에 런타임 필수
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.batch)
    implementation(libs.spring.boot.starter.data.jpa)   // JPA 자동 구성 — persistence 모듈의 리포지토리 활성화
    implementation(libs.spring.boot.starter.kafka)
    implementation(platform(libs.spring.cloud.aws.bom)) // s3-client의 AWS SDK 버전 정렬
    runtimeOnly(libs.mysql.connector.j)                 // 스위퍼가 outbox 테이블을 읽으므로 실 DB 필요. 마이그레이션은 api만 수행
    testImplementation(testFixtures(project(":api")))   // Testcontainers MySQL·Redis 컨테이너 배선(IntegrationTestConfig.registerContainers)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.mysql)
}
