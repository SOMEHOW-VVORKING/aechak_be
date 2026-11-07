// application 검색 포트의 Elasticsearch 어댑터 모듈. 클라이언트 타입은 이 모듈 밖으로 노출하지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)      // @Repository 스테레오타입
    implementation(libs.spring.tx)           // QueryTimeoutException
    implementation(libs.spring.boot.starter.elasticsearch)
}
