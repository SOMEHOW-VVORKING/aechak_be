// 소셜 id_token 검증 어댑터 모듈(카카오·애플 OIDC). 포트는 application이 소유하고 구현만 여기 둔다.
// Nimbus(JWKS) 의존은 이 모듈 밖으로 새지 않는다 — 검증 실패는 BusinessException으로 번역해 던진다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)                 // @Component 스테레오타입
    implementation(libs.spring.boot)                    // @ConfigurationProperties 바인딩
    implementation(libs.spring.security.oauth2.jose)    // NimbusJwtDecoder — 모듈 내부 격리
    implementation(libs.spring.web)                     // JWKS 페칭 RestTemplate(타임아웃 명시)
}
