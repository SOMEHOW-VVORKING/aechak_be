// SMS 발송 어댑터 모듈. 포트(SmsSender)는 application이 소유하고 구현만 여기 둔다.
// 실벤더(CoolSMS) 어댑터도 이 모듈에 후행 추가된다 — 벤더 HTTP 어휘는 모듈 밖으로 새지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)      // @Component·@Profile 스테레오타입
    implementation(libs.slf4j.api)           // 로깅 API만 — 구현(logback)은 실행 모듈이 공급
    testImplementation(libs.spring.boot.starter.test) // ApplicationContextRunner — 프로파일 배선 검증
}
