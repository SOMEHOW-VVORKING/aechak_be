// 도메인 모델 모듈. Spring을 모른다 — 허용 의존은 common과 (컨벤션이 제공하는) jakarta.persistence-api뿐.
plugins { id("aechak.jpa-entity") }
dependencies {
    api(project(":common"))                  // 도메인이 던지는 BusinessException/ErrorCode 규약
}
