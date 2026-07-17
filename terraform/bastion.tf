# 개발자 DB 접속용 SSM bastion (점프서버)
# - 공인 IP 없음, 열린 포트 0개 — 접속은 오직 aws ssm start-session (IAM 인증, CloudTrail 기록)
# - 서비스 트래픽과 무관: 죽어도 앱은 멀쩡, 복구는 apply 한 번 (상태 없는 빈 껍데기)
# - 용도: SSM 포트포워딩으로 RDS 터널 (DataGrip 등) — 사용법: README '자주 하는 작업' 및 노션 '애착 인프라 가이드 > 4. RDS 접속'

# t4g는 ARM(Graviton) — x86용 al2023 AMI와 별도
data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-kernel-*-arm64"]
  }
}

resource "aws_security_group" "bastion" {
  name_prefix = "${var.project}-bastion-"
  vpc_id      = aws_vpc.main.id

  # ingress 없음 — SSM은 아웃바운드 기반이라 들어오는 문이 필요 없다

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] # SSM 채널(NAT 경유) + RDS 등 내부 접속
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_instance" "bastion" {
  ami                    = data.aws_ami.al2023_arm64.id
  instance_type          = "t4g.nano" # ~$3/월 — 터널 중계만 하므로 최소 사양
  subnet_id              = aws_subnet.data_a.id
  vpc_security_group_ids = [aws_security_group.bastion.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name # SSM core만 있는 공용 롤 재사용

  lifecycle {
    ignore_changes = [ami]
  }

  root_block_device {
    volume_size = 8
    volume_type = "gp3"
  }

  tags = { Name = "${var.project}-bastion-${var.env}" }
}

output "bastion_instance_id" {
  value       = aws_instance.bastion.id
  description = "RDS 터널: aws ssm start-session --target <이 값> --document-name AWS-StartPortForwardingSessionToRemoteHost"
}
