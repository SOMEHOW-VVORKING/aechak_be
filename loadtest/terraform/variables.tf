variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "datasets" {
  description = "측정층 RDS를 만들 데이터 크기 목록"
  type        = list(string)
  default     = ["100k", "1m", "10m"]
}

variable "db_storage_gb" {
  description = "데이터 크기별 RDS 저장 공간(GB). 목록에 없는 크기는 20GB"
  type        = map(number)
  default     = { "10m" = 40 }
}

variable "db_instance_class" {
  type    = string
  default = "db.m7g.large"
}

variable "db_engine_version" {
  description = "시리즈 내내 고정할 MySQL 버전"
  type        = string
  default     = "8.4.11"
}

variable "mirror_snapshot_id" {
  description = "운영 거울층 db.t4g.micro를 복원할 스냅샷. null이면 만들지 않는다"
  type        = string
  default     = null
}

variable "es_enabled" {
  description = "ES 호스트 생성 여부"
  type        = bool
  default     = false
}

variable "es_instance_type" {
  description = "ES 호스트 타입. arm64만 쓴다(m7g.large, 저사양 셀은 t4g.small)"
  type        = string
  default     = "m7g.large"
}

variable "auto_stop_enabled" {
  description = "make down을 잊어도 매일 perf EC2와 RDS를 멈추는 스케줄 생성 여부"
  type        = bool
  default     = true
}

variable "auto_stop_cron" {
  description = "자동 정지 시각. Asia/Seoul 기준 EventBridge Scheduler cron 식"
  type        = string
  default     = "cron(0 5 * * ? *)"
}
