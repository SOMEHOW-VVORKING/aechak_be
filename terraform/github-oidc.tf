# GitHub Actions → AWS 인증 (OIDC — 장수 액세스키 없음)
# 워크플로가 role-to-assume으로 이 역할을 임시 획득한다

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # develop(통합 브랜치, 레포 기본)에서만 assume 가능 — dev 환경 배포 트리거와 세트
    # prod 생기면: main용 별도 역할 + deploy-prod.yml 추가 (develop→dev, main→prod 매핑)
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/develop"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.project}-github-deploy-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [
      aws_ecr_repository.app.arn,
      aws_ecr_repository.seller_api.arn,
      aws_ecr_repository.admin.arn,
      aws_ecr_repository.batch.arn,
    ]
  }

  # ECS 배포: 새 taskdef 리비전 등록 + 서비스 갱신
  statement {
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"] # 이 두 액션은 리소스 레벨 제한 미지원
  }

  statement {
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.me.account_id}:service/${var.project}-${var.env}/${var.project}-api-${var.env}",
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.me.account_id}:service/${var.project}-${var.env}/${var.project}-seller-api-${var.env}",
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.me.account_id}:service/${var.project}-${var.env}/${var.project}-admin-${var.env}",
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.me.account_id}:service/${var.project}-${var.env}/${var.project}-batch-${var.env}",
    ]
  }

  # taskdef 등록 시 execution/task 롤을 ECS에 넘겨줄 권한
  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_execution.arn, aws_iam_role.ecs_task.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

output "github_deploy_role_arn" {
  value       = aws_iam_role.github_deploy.arn
  description = "GitHub repo secrets에 AWS_DEPLOY_ROLE_ARN으로 등록"
}

# ── CI terraform apply용 역할 — 배포 역할과 분리 ──────────
# terraform은 IAM 자체를 만들고 지우므로 권한을 좁힐 방법이 사실상 없다(최소권한 불가).
# 실질 방어선: ①OIDC 신뢰조건(이 레포 develop에서만 assume) ②prod 봉인(variables.tf가 env=dev만 허용) ③CloudTrail
resource "aws_iam_role" "github_terraform" {
  name               = "${var.project}-github-terraform-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

resource "aws_iam_role_policy_attachment" "github_terraform_admin" {
  role       = aws_iam_role.github_terraform.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

output "github_terraform_role_arn" {
  value       = aws_iam_role.github_terraform.arn
  description = "GitHub repo secrets에 AWS_TERRAFORM_ROLE_ARN으로 등록"
}

# ── AI 레포(aechak_ai) 전용 인증 ──────────────────
# 위 github_assume을 재사용하지 않는다. 그 문서는 배포 역할과 **AdministratorAccess가 붙은**
# terraform 역할이 함께 쓰기 때문에, values에 레포를 하나 더 넣으면 AI 레포 워크플로가
# 계정 관리자 역할까지 맡을 수 있게 된다. 역할 ARN을 시크릿에 안 넣는 것으로는 막히지 않는다.
data "aws_iam_policy_document" "github_assume_ai" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo_ai}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_deploy_ai" {
  name               = "${var.project}-github-deploy-ai-${var.env}"
  assume_role_policy = data.aws_iam_policy_document.github_assume_ai.json
}

data "aws_iam_policy_document" "github_deploy_ai" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.ai.arn] # AI 레포는 자기 이미지만 밀어넣는다
  }

  statement {
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"] # 이 두 액션은 리소스 레벨 제한 미지원
  }

  statement {
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.me.account_id}:service/${var.project}-${var.env}/${var.project}-ai-${var.env}",
    ]
  }

  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_execution_ai.arn, aws_iam_role.ecs_task_ai.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy_ai" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy_ai.id
  policy = data.aws_iam_policy_document.github_deploy_ai.json
}

output "github_deploy_ai_role_arn" {
  value       = aws_iam_role.github_deploy_ai.arn
  description = "aechak_ai 레포 시크릿에 AWS_DEPLOY_ROLE_ARN으로 등록"
}

output "ai_ecr_repository_url" {
  value       = aws_ecr_repository.ai.repository_url
  description = "aechak_ai 레포 워크플로가 push할 대상"
}
