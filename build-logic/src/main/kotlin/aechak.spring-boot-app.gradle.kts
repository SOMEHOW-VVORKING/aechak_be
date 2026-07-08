// 실행 모듈(api·batch) 컨벤션: bootJar/bootRun을 제공한다.
plugins {
    id("aechak.spring-library")
    id("org.springframework.boot")
}
// io.spring.dependency-management는 쓰지 않는다 — BOM은 이미 platform()으로 적용(Gradle 네이티브 방식)
