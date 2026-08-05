// HTTP 번역 계층: 응답 규격·전역 예외 핸들러·TraceId 필터. 웹 계열 모듈(실행 모듈·web-security)이 의존한다.
// domain/application은 이 모듈을 절대 의존하지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    api(project(":common"))                  // 실행 모듈이 ErrorCode/BusinessException을 직접 쓰므로 api로 노출
    implementation(libs.spring.web)          // ResponseEntity·@RestControllerAdvice·필터 등 HTTP 번역용
    compileOnly(libs.spring.context)         // OncePerRequestFilter의 상위 타입(EnvironmentAware)이 spring-context 소속 — 컴파일에만 필요
    compileOnly(libs.jakarta.servlet.api)    // 런타임은 실행 모듈의 내장 톰캣이 제공
    compileOnly(libs.jakarta.validation.api) // 커스텀 제약(@Constraint/ConstraintValidator) 정의용. 런타임은 실행 모듈의 starter-validation이 제공
    compileOnly(libs.spring.tx)              // 낙관적 락 충돌을 409로 옮기는 핸들러용. 런타임은 실행 모듈이 제공
    implementation(libs.slf4j.api)           // MDC(traceId) 연동
}
