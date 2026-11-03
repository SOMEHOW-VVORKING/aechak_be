data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

data "aws_ssm_parameter" "al2023_x86_64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  hosts = {
    runner = { ami = data.aws_ssm_parameter.al2023_arm64.insecure_value, type = "c7g.large", disk = 30, sg = aws_security_group.runner.id }
    api    = { ami = data.aws_ssm_parameter.al2023_x86_64.insecure_value, type = "m7i.large", disk = 20, sg = aws_security_group.api.id }
    es     = { ami = data.aws_ssm_parameter.al2023_arm64.insecure_value, type = var.es_instance_type, disk = 40, sg = aws_security_group.es.id }
  }
  enabled_hosts = { for k, v in local.hosts : k => v if k != "es" || var.es_enabled }
}

resource "aws_instance" "host" {
  for_each = local.enabled_hosts

  ami                         = each.value.ami
  instance_type               = each.value.type
  subnet_id                   = data.aws_subnet.public_a.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [each.value.sg]
  iam_instance_profile        = aws_iam_instance_profile.host[each.key].name

  # 컨테이너도 인스턴스 역할을 쓰도록 hop 2
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = each.value.disk
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    role   = each.key
    bucket = aws_s3_bucket.artifacts.bucket
    region = var.region
  })

  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = { Name = "aechak-perf-${each.key}" }
}
