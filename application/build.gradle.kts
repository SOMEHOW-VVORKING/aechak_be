// 유스케이스 조율 계층. HTTP(web-common/spring-web)와 infra 기술을 모른다.
plugins { id("aechak.spring-library") }
dependencies {
    api(project(":domain"))                  // Command/Result 시그니처가 domain 타입을 노출하므로 api
    implementation(libs.spring.context)      // @Service, ApplicationEventPublisher
    implementation(libs.spring.tx)           // @Transactional — 경계는 Facade 고정
    implementation(libs.slf4j.api)           // 로깅 API만 — 구현(logback)은 실행 모듈이 공급
    // 리포지토리는 domain의 포트를 주입받는다. Spring Data는 이 모듈에 두지 않는다.
}
