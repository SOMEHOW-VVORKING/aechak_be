env = "dev"

kafka_instance_type = "t3.small"
db_instance_class   = "db.t4g.micro"

# 세 웹 앱의 로컬 dev 포트. 배포 도메인은 terraform locals가 붙이므로 여기엔 로컬 포트만 적는다.
frontend_origins = ["http://localhost:5175"] # 구매자 웹 dev

seller_frontend_origins = ["http://localhost:5173"] # 셀러센터 웹 dev

admin_frontend_origins = ["http://localhost:5174"] # 어드민 웹 dev

# 배치 스케줄 — 전제가 충족된 넷만 켠다. 적지 않은 셋은 기본값 false로 등재되고,
# 켜지 못하는 근거는 variables.tf 의 해당 필드 주석에 적혀 있다.
batch_schedules_enabled = {
  order_expiry         = true # 결제 시도 흔적이 없는 건(장바구니 이탈, 대부분)은 PG 조회 없이 만료된다
  order_auto_confirm   = true # 구매확정 이후 생애주기(반품 기한·정산)를 dev에서 돌려 보려면 이게 기점이다
  order_delivery       = true # 배치 모듈이 dev에 배포돼 있다(ECS aechak-batch-dev, desired 1 / running 1)
  settlement_execution = true # PG 호출 없이 지급 건만 만든다 — dev DB 안에서만 일어난다
  # delivery_tracking  : 목 어댑터뿐이라 켜면 '[모의]' 가짜 상태가 dev DB에 쌓인다
  # claim_refund       : /aechak/dev/batch 에 PORTONE_API_SECRET·PORTONE_STORE_ID 가 아직 없다(수기 등재 대상)
  # claim_auto_approve : claim_refund 가 전제다
}
