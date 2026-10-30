# ── 변수가 채워지는 방식 ──────────────────────────────────────
# 여기는 "선언"만 한다 (함수의 파라미터 선언부에 해당).
# 실제 값의 우선순위:  -var CLI  >  -var-file(envs/*.tfvars)  >  아래 default
# 모든 .tf 파일에서 var.project 처럼 참조하면 그 값으로 치환된다.
# 파일이 달라도 참조 가능 — 테라폼은 이 디렉토리의 .tf 전부를 하나로 합쳐 읽는다.
# ──────────────────────────────────────────────────────────────

variable "project" {
  type    = string
  default = "aechak" # 아무도 안 덮어쓰므로 항상 이 값
}

variable "env" {
  # default가 없는 건 의도 — envs/*.tfvars 없이 실행하면 조기 실패하게 (dev/prod 혼동 방지)
  type = string # dev | prod

  # [가드] prod는 state 분리 전까지 봉인 — 지금 prod.tfvars로 apply하면 같은 backend key라
  # dev state를 덮어써 dev 전체가 파괴된다. prod 착수 시: state 전략(workspace/key 분리) 결정
  # → 이 validation 해제 → multi-AZ 등 승격 체크리스트(kb/20) 수행. 그 전엔 코드가 거부한다.
  validation {
    condition     = var.env == "dev"
    error_message = "env=prod는 아직 봉인됨: state 분리(workspace 또는 backend key) 없이 apply하면 dev state를 덮어씀. terraform/kb/20-current-state.md의 prod 승격 체크리스트 참조."
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# 실 리소스는 전부 az_main(2a)에 몰아둔다 — AZ 간 전송비/지연 회피.
# az_sub(2c)는 ALB·subnet group의 2-AZ 요건 충족용.
variable "az_main" {
  type    = string
  default = "ap-northeast-2a"
}

variable "az_sub" {
  type    = string
  default = "ap-northeast-2c"
}

variable "app_port" {
  type    = number
  default = 8080
}

# 앱 사이징은 인스턴스 타입이 아니라 ecs.tf taskdef의 cpu/memory (Fargate — 서버 선택 없음)

# Kafka 힙 + OS 여유가 빠듯하면 t3.medium으로 올릴 것
variable "kafka_instance_type" {
  type    = string
  default = "t3.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "aws_account_id" {
  type        = string
  description = "애착 AWS 계정 (마에스트로 Innovation Sandbox 리스 계정 ISB-56) — 가드레일용"
  default     = "447170313132"
}

variable "github_repo" {
  type        = string
  description = "GitHub Actions OIDC 허용 대상 (org/repo)"
  default     = "SOMEHOW-VVORKING/aechak_be"
}

# AI 레포는 별 변수로 받는다. 위 변수에 값을 더하면 배포 역할과 terraform 역할이
# 신뢰 문서를 공유해서 AI 레포가 관리자 역할까지 맡게 된다.
variable "github_repo_ai" {
  type        = string
  description = "AI 검수 서버 레포. 전용 OIDC 신뢰 문서에만 쓰인다 (org/repo)"
  default     = "SOMEHOW-VVORKING/aechak_ai"
}

variable "frontend_origins" {
  type        = list(string)
  description = "구매자 웹의 로컬 개발 오리진 (포트별 복수). 배포 도메인은 locals가 붙인다"
}

variable "inquiry_notification_enabled" {
  type        = bool
  default     = false
  description = "문의 접수 시 운영팀 이메일 통지 켜기. 켜면 SES 검증 + inquiry_ops_recipients 필수."
}

variable "inquiry_ops_recipients" {
  type        = list(string)
  default     = []
  description = "문의 통지 수신 운영팀 이메일(복수). notification 켤 때 필수."

  validation {
    condition = !var.inquiry_notification_enabled || (
      length(var.inquiry_ops_recipients) > 0 &&
      alltrue([for e in var.inquiry_ops_recipients : length(trimspace(e)) > 0 && strcontains(e, "@")])
    )
    error_message = "inquiry_notification_enabled=true 이면 inquiry_ops_recipients에 유효한 이메일이 최소 1개 필요합니다."
  }
}

variable "seller_frontend_origins" {
  description = "셀러센터 웹의 로컬 개발 오리진 (포트별 복수). 배포 도메인은 locals가 붙인다"
  type        = list(string)
  default     = []
}

variable "admin_frontend_origins" {
  description = "어드민 웹의 로컬 개발 오리진 (포트별 복수). 배포 도메인은 locals가 붙인다"
  type        = list(string)
  default     = []
}

# AI 검수 서버는 uvicorn이라 자바 모듈(8080)과 포트가 다르다.
variable "ai_app_port" {
  description = "AI 검수 서버 컨테이너 포트"
  type        = number
  default     = 8000
}

# 처음 apply는 0으로 둔다. taskdef가 :bootstrap 태그를 가리키는데 그 태그가 ECR에 없어서,
# 1로 두면 존재하지 않는 이미지를 pull하려는 태스크가 실패 루프에 빠진다.
# aechak_ai 레포의 첫 배포가 실제 태그를 올린 뒤 이 값을 1로 올린다.
variable "ai_desired_count" {
  description = "AI 검수 서버 태스크 수. 첫 이미지가 올라간 뒤 1로 올린다"
  type        = number
  default     = 0
}

# 배치 보트의 스케줄 스위치 일곱 개. 기본값은 코드와 같게 전부 false로 둔다 —
# tfvars에 안 적힌 플래그가 조용히 켜지면 돈이 나가거나 비가역 전이가 일어난다.
# 켜기 전 전제는 플래그마다 다르다. 아래 주석이 요약이고 정본은
# boot/batch/src/main/resources/application.yml 의 주석이다.
variable "batch_schedules_enabled" {
  type = object({
    # 미결제 주문그룹 만료. 주문그룹 생성이 재고·적립금을 먼저 깎으므로 꺼 두면 그만큼 자산이 묶인다.
    # 결제 시도 흔적이 있는 건만 PG를 조회한다 — 그 경로의 PORTONE_API_SECRET이 없으면 그 건은 못 만료시킨다.
    order_expiry = optional(bool, false)

    # 배송완료 후 기한이 지난 주문의 구매확정 + 기한 D-1 사전 안내.
    # 확정은 비가역이고 반품 기한·정산의 기산점이다.
    order_auto_confirm = optional(bool, false)

    # 송장 등록 후 일수만 보고 배송완료로 전이하고, 집화 지연은 경고로 남긴다.
    # 배치 모듈이 그 환경에 실제로 배포돼 있어야 켠다 — 실물 배송과 무관하게 시간만 보기 때문이다.
    order_delivery = optional(bool, false)

    # 택배사 추적 갱신. 실제 택배사 어댑터가 붙은 환경에서만 켠다.
    # 지금 조립되는 어댑터가 목뿐이라, 켜면 '[모의]' 표식이 붙은 가짜 상태가 DB에 쌓여
    # 구매자·셀러 화면에 그대로 뜬다. order_delivery 가 auto_complete_days 로 배송완료까지
    # 진행시키므로, 추적 없이도 주문 생애주기는 끝까지 돈다.
    delivery_tracking = optional(bool, false)

    # 승인된 취소 클레임의 환불 집행. 실제로 PG로 돈이 나가는 유일한 배치다.
    # batch 경로에 PORTONE_API_SECRET·PORTONE_STORE_ID 가 등재돼 있고, 그 상점이
    # 테스트 상점인지 확인한 뒤 켠다. PAYMENT_CANCEL_MAX_AMOUNT 가 1건 금액 상한이다.
    claim_refund = optional(bool, false)

    # 셀러 무응답 클레임 자동승인. 자동승인은 환불 집행까지 이어지므로
    # claim_refund 가 false면 켜도 스케줄이 조립되지 않는다(코드가 두 플래그를 AND로 본다).
    claim_auto_approve = optional(bool, false)

    # 정산 지급 건 생성. PG 호출은 없지만, 지급 건에 묶인 정산은 다음 회차가 다시 집지 않아
    # 되돌리기가 쉽지 않다. 대사(04:30)가 끝난 뒤 05:00에 돈다.
    settlement_execution = optional(bool, false)
  })
  default     = {}
  description = "배치 스케줄 on/off. 전부 미설정이면 배치 보트는 떠 있어도 스케줄이 하나도 돌지 않는다."
}
