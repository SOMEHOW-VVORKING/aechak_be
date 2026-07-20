// 요청 보안 "판단 부품"(토큰 코덱·상태 필터) 공용 모듈 — 어느 실행 모듈에서든 동일하게 동작한다.
// 경로 정책 조립(SecurityConfig — permitAll·CORS·EntryPoint)은 각 실행 모듈이 소유한다.
// admin 등 새 웹 실행 모듈은 이 모듈만 의존하면 같은 토큰 해독·상태 차단을 얻는다.
// 빈 활성화 전제: 실행 모듈의 컴포넌트 스캔이 com.aechak 루트를 포함해야 한다(ApiApplication 참조).
plugins { id("aechak.spring-library") }
dependencies {
    api(project(":application"))              // UserStatusFilter 생성자·tokenPolicy 빈 시그니처가 application 타입을 노출하므로 api
    implementation(project(":web-common"))    // 필터가 403 응답 본문에 쓰는 에러 응답 형식(ErrorResponse)
    implementation(libs.spring.boot.starter.oauth2.resource.server) // JwtEncoder/Decoder·Nimbus·JwtAuthenticationToken
    implementation(libs.spring.boot)          // @ConfigurationProperties 바인딩 (토큰 TTL·JWT 키)
    implementation(libs.jackson.databind)     // 필터가 에러 응답을 직접 JSON으로 써야 해서 (Boot 4 = Jackson 3)
    implementation(libs.slf4j.api)            // 임시 키 생성 경고 로그
    compileOnly(libs.jakarta.servlet.api)     // 런타임은 실행 모듈의 내장 톰캣이 제공
}
