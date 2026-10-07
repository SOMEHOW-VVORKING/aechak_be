// SMS 발송 어댑터 모듈. 포트(SmsSender)는 application이 소유하고 구현만 여기 둔다.
// CoolSMS SDK(Retrofit·OkHttp 동봉) 의존은 이 모듈 밖으로 새지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)      // @Component·@Profile 스테레오타입
    implementation(libs.spring.boot)         // @ConfigurationProperties 바인딩
    implementation(libs.slf4j.api)           // 로깅 API만 — 구현(logback)은 실행 모듈이 공급
    implementation(libs.solapi.sdk)          // CoolSMS 실발송 SDK
    testImplementation(libs.spring.boot.starter.test) // ApplicationContextRunner — 프로파일 배선 검증
}
