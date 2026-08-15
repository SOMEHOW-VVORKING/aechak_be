resource "aws_ecr_repository" "app" {
  name = "${var.project}-api" # boot/api 모듈 배포용. batch 이미지화 시 aechak-batch 추가

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 최근 10개만 유지 — 이미지 쌓임 방지
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 10"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_repository" "seller_api" {
  name = "${var.project}-seller-api" # boot/seller 모듈 배포용 (SCRUM-193)

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "seller_api" {
  repository = aws_ecr_repository.seller_api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 10"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
