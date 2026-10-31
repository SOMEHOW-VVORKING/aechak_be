// AI 서버(셀러 폼 초안) 호출 어댑터 모듈. 포트는 application이 소유하고 구현만 여기 둔다.
// AI의 요청/응답 dto는 이 모듈 밖으로 새지 않는다 — 외부 실패는 UNAVAILABLE 결과로 번역한다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)                 // @Component·@Configuration·@Conditional 스테레오타입
    implementation(libs.spring.boot)                    // @ConfigurationProperties 바인딩
    implementation(libs.spring.web)                     // RestClient. 초안 동기 호출
    implementation(libs.jackson.databind)               // 초안 요청/응답 JSON. RestClient 기본 컨버터가 사용
    implementation(libs.jackson.module.kotlin)          // 없으면 기본값 없는 응답 data class 역직렬화가 깨짐
    implementation(libs.slf4j.api)                      // 로깅 API만. 구현(logback)은 실행 모듈이 공급
    testImplementation(libs.spring.boot.starter.test)   // MockRestServiceServer·ApplicationContextRunner
}
