// API 실행 모듈이자 조립 지점. infra 구현체를 컨테이너에 꽂는 곳은 실행 모듈뿐이다.
plugins { id("aechak.spring-boot-app") }
dependencies {
    implementation(project(":web-common"))
    implementation(project(":application"))
    implementation(project(":jpa-persistence"))         // 도메인 포트의 구현체 조립. 다른 infra 모듈도 어댑터가 생기면 여기 추가
    implementation(kotlin("reflect"))                   // Spring(Data)의 Kotlin 리플렉션 지원에 런타임 필수
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation) // Request dto의 @Valid 형식 검증
    implementation(libs.spring.boot.starter.data.jpa)   // JPA 자동 구성 — persistence 모듈의 리포지토리 활성화
    implementation(libs.spring.boot.starter.oauth2.resource.server) // 자체 RS256 토큰 검증 필터 + JwtEncoder/Decoder
    runtimeOnly(libs.h2)                                // 임시 내장 DB — ERD 확정 시 실 DB로 교체
    testImplementation(libs.spring.boot.starter.test)  // 컨텍스트 부팅 스모크 테스트(엔티티 스키마 생성 검증)
}
