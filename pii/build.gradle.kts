// PII 암호화 공용 모듈 — 엔진(AES-GCM 키링·HMAC)과 키 조립(PiiCryptoConfig)을 함께 소유한다.
// web-security와 같은 결: 어느 실행 모듈에서든 동일해야 하는 조립(같은 키로 같은 암호문을 복호)은 공유 모듈이 소유하고,
// 실행 모듈은 의존 + com.aechak.pii 스캔으로 켠다. 키(aechak.pii.*) 미주입이면 부팅 실패(fail-fast).
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))   // PiiCrypto 포트·PiiContext 라벨
    implementation(libs.spring.context)       // @Configuration
    implementation(libs.spring.boot)          // @ConfigurationProperties 바인딩 (PII 키)
    implementation(libs.spring.security.crypto) // AES-GCM 엔진
}
