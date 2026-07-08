// PG 결제 외부 API 어댑터 모듈(TossPayments 등). 포트는 application이 소유하고 구현만 여기 둔다.
// 외부사의 요청/응답 dto는 이 모듈 밖으로 새지 않는다 — 외부 장애는 BusinessException으로 번역해 던진다.
plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    // TODO: 어댑터 코드가 생기는 커밋에서 기술 의존성(RestClient 등) 등록
}
