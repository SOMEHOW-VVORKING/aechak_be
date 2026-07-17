// application 포트(RefreshTokenStore)의 Redis 어댑터 모듈.
// RedisTemplate 등 Spring Data Redis 타입은 이 모듈 밖으로 노출하지 않는다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(libs.spring.context)      // @Component 스테레오타입
    implementation(libs.spring.data.redis)
}
