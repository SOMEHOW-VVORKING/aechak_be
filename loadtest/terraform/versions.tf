terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # 팀 루트와 같은 버킷, 다른 key
  backend "s3" {
    bucket       = "aechak-tfstate-447170313132"
    key          = "perf/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  # 애착 계정이 아니면 plan과 apply를 거부한다
  allowed_account_ids = ["447170313132"]

  default_tags {
    tags = {
      Project   = "aechak-perf"
      ManagedBy = "terraform"
    }
  }
}
