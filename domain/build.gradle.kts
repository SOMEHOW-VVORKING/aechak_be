// 도메인 모델 모듈. Spring을 모른다 — 허용 의존은 common, (컨벤션의) jakarta.persistence-api, ulid-creator뿐.
plugins { id("aechak.jpa-entity") }
dependencies {
    api(project(":common"))                  // 도메인이 던지는 BusinessException/ErrorCode 규약
    implementation(libs.ulid.creator)         // publicId(ULID) 채번 — 전이 의존 0 순수 Java (support/Ulid 파사드로 격리)
}
