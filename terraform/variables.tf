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

variable "frontend_origins" {
  type        = list(string)
  description = "presigned 업로드 CORS 허용 오리진 (웹 dev 포트별 복수)"
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
  description = "셀러센터 웹 로컬 개발 오리진 — 배포된 셀러 웹 도메인이 생기면 CORS 로컬에 그 값을 합류시킨다"
  type        = list(string)
  default     = []
}
