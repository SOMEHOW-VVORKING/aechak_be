// 택배사 추적 조회 어댑터 모듈. 포트는 application이 소유하고 구현만 여기 둔다.
// 현재 구현은 외부를 부르지 않는 목 하나 — 실제 택배사 계약이 생기면 이 모듈에 어댑터를 더하고 목을 끈다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)                 // @Component·@Conditional·@Profile 스테레오타입
    implementation(libs.slf4j.api)                      // 로깅 API만. 구현(logback)은 실행 모듈이 공급
    testImplementation(libs.spring.boot.starter.test)   // ApplicationContextRunner로 조립 조건 검증
}
