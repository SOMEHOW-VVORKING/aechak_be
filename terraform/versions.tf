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

  backend "s3" {
    bucket       = "aechak-tfstate-334455667515"
    key          = "main/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true # TF 1.10+ S3 네이티브 잠금 (DynamoDB 불필요)
  }
}

data "aws_caller_identity" "me" {}

provider "aws" {
  region = var.region

  # 가드레일: 애착 계정이 아니면 plan/apply 자체를 거부 (타 계정 오적용 방지)
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project   = var.project
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}
