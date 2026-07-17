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
