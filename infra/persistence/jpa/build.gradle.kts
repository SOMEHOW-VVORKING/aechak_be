// domain 리포지토리 포트의 JPA 어댑터 모듈. Spring Data 타입은 이 모듈 밖으로 노출하지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))  // domain은 application의 api로 전이 — 포트·엔티티 참조용
    implementation(libs.spring.context)      // @Repository 스테레오타입
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.boot.starter.flyway) // Flyway 마이그레이션
    implementation(libs.flyway.mysql)
}
