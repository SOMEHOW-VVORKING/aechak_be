data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

locals {
  account_id = "447170313132"
  bucket_arn = aws_s3_bucket.artifacts.arn
  param_arn  = "arn:aws:ssm:${var.region}:${local.account_id}:parameter/aechak-perf/*"
  rds_arn    = "arn:aws:rds:${var.region}:${local.account_id}:db:aechak-perf-*"
  log_arns = concat(
    [aws_cloudwatch_log_group.api.arn, "${aws_cloudwatch_log_group.api.arn}:*"],
    flatten([for g in aws_cloudwatch_log_group.slowquery : [g.arn, "${g.arn}:*"]]),
  )

  # SecureString 복호화는 SSM을 거칠 때만
  kms_statement = {
    Effect    = "Allow"
    Action    = "kms:Decrypt"
    Resource  = data.aws_kms_alias.ssm.target_key_arn
    Condition = { StringEquals = { "kms:ViaService" = "ssm.${var.region}.amazonaws.com" } }
  }

  # 모든 호스트: kit-pull과 SSM 명령 출력 업로드
  common_statements = [
    {
      Effect   = "Allow"
      Action   = "s3:GetObject"
      Resource = ["${local.bucket_arn}/kit/*", "${local.bucket_arn}/state/*"]
    },
    {
      Effect   = "Allow"
      Action   = "s3:PutObject"
      Resource = "${local.bucket_arn}/ssm-out/*"
    },
  ]

  host_statements = {
    api = concat(local.common_statements, [
      {
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${local.bucket_arn}/jars/*"
      },
      {
        Effect    = "Allow"
        Action    = "s3:ListBucket"
        Resource  = local.bucket_arn
        Condition = { StringLike = { "s3:prefix" = ["jars/*", "kit/*", "state/*"] } }
      },
      {
        Effect   = "Allow"
        Action   = "ssm:GetParameter"
        Resource = aws_ssm_parameter.db_master.arn
      },
      local.kms_statement,
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
        Resource = [aws_cloudwatch_log_group.api.arn, "${aws_cloudwatch_log_group.api.arn}:*"]
      },
    ])

    runner = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${local.bucket_arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = local.bucket_arn
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = local.param_arn
      },
      local.kms_statement,
      {
        Effect   = "Allow"
        Action   = ["cloudwatch:GetMetricData", "cloudwatch:ListMetrics", "cloudwatch:GetMetricStatistics"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["logs:FilterLogEvents", "logs:GetLogEvents", "logs:DescribeLogStreams", "logs:StartQuery", "logs:GetLogGroupFields"]
        Resource = local.log_arns
      },
      {
        Effect   = "Allow"
        Action   = ["logs:GetQueryResults", "logs:StopQuery", "logs:DescribeLogGroups"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["ec2:DescribeInstances", "ec2:DescribeTags", "ec2:DescribeRegions", "tag:GetResources"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "rds:DescribeDBInstances"
        Resource = local.rds_arn
      },
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = concat(
          [for k, h in aws_instance.host : h.arn if k != "runner"],
          ["arn:aws:ssm:${var.region}::document/AWS-RunShellScript"],
        )
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
    ]

    es = local.common_statements
  }
}

resource "aws_iam_role" "host" {
  for_each = local.enabled_hosts
  name     = "aechak-perf-${each.key}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  for_each   = local.enabled_hosts
  role       = aws_iam_role.host[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "host" {
  for_each = local.enabled_hosts
  name     = "aechak-perf-${each.key}"
  role     = aws_iam_role.host[each.key].id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = local.host_statements[each.key]
  })
}

resource "aws_iam_instance_profile" "host" {
  for_each = local.enabled_hosts
  name     = "aechak-perf-${each.key}"
  role     = aws_iam_role.host[each.key].name
}
