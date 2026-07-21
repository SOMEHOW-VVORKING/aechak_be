// API 실행 모듈이자 조립 지점. infra 구현체를 컨테이너에 꽂는 곳은 실행 모듈뿐이다.
plugins { id("aechak.spring-boot-app") }
dependencies {
    implementation(project(":web-common"))
    implementation(project(":web-security"))            // 토큰 코덱·상태 필터 등 요청 보안 판단 부품 — 정책 조립(SecurityConfig)은 api 소유
    implementation(project(":application"))
    implementation(project(":jpa-persistence"))         // 도메인 포트의 구현체 조립. 다른 infra 모듈도 어댑터가 생기면 여기 추가
    implementation(project(":social-client"))           // 소셜 id_token 검증 어댑터(ACC-01)
    implementation(project(":redis"))                   // refresh token 저장소 어댑터(ACC-01)
    implementation(project(":s3-client"))               // presigned URL 발급·승격 어댑터
    implementation(kotlin("reflect"))                   // Spring(Data)의 Kotlin 리플렉션 지원에 런타임 필수
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation) // Request dto의 @Valid 형식 검증
    implementation(libs.spring.boot.starter.data.jpa)   // JPA 자동 구성 — persistence 모듈의 리포지토리 활성화
    implementation(libs.spring.boot.starter.oauth2.resource.server) // 자체 RS256 토큰 검증 필터 + JwtEncoder/Decoder
    implementation(libs.spring.boot.starter.data.redis) // Redis 자동 구성 — :redis 어댑터의 커넥션 제공
    implementation(libs.springdoc.webmvc.ui)            // OpenAPI 3 문서 + Swagger UI
    implementation(libs.spring.boot.starter.actuator)   // ALB 헬스체크 /actuator/health — ECS 롤링·롤백의 생사판단 기준 (health만 노출)
    implementation(platform(libs.spring.cloud.aws.bom)) // Spring Cloud AWS 계열 버전 정렬 — 개별 버전 지정 시 SDK 버전 충돌
    implementation(libs.spring.cloud.aws.starter.parameter.store) // dev 설정을 부팅 시 SSM에서 로드 — import 미선언 프로파일에선 비활성
    runtimeOnly(libs.spring.boot.starter.flyway)        // API 모듈만 DB 마이그레이션을 수행
    runtimeOnly(libs.flyway.mysql)                      // Flyway 10+ MySQL 지원 모듈
    runtimeOnly(libs.mysql.connector.j)                 // 실 DB(MySQL 8.4) 드라이버 — 접속정보는 프로파일/env가 주입
    testImplementation(libs.spring.boot.starter.test)  // 컨텍스트 부팅 스모크 테스트(엔티티 스키마 생성 검증)
    testImplementation(libs.spring.boot.testcontainers) // @ServiceConnection — 통합 테스트 DB를 실 MySQL 컨테이너로 배선(70 §9)
    testImplementation(libs.testcontainers.mysql)       // MySQL 컨테이너 모듈. H2는 두지 않는다 — 폴백 통과(false confidence) 차단
}
