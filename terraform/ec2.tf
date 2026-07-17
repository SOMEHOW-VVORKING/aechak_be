data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-kernel-*-x86_64"]
  }
}

# 앱 실행 레이어는 ECS Fargate (ecs.tf) — 여기는 Kafka EC2만

# ── Kafka (자체 호스팅, KRaft 단일노드) ────────────────
# 고정 사설 IP → advertised.listeners·앱 설정에 그대로 사용
locals {
  kafka_private_ip = "10.0.20.10"
}

resource "aws_instance" "kafka" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.kafka_instance_type
  subnet_id              = aws_subnet.data_a.id
  private_ip             = local.kafka_private_ip
  vpc_security_group_ids = [aws_security_group.kafka.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  user_data = <<-EOF
    #!/bin/bash
    dnf install -y docker
    systemctl enable --now docker
    # apache/kafka 이미지는 UID 1000(non-root)으로 실행 — 호스트 볼륨을 그 유저 소유로 만들어야
    # 데이터 디렉토리 쓰기가 됨 (안 하면 meta.properties 쓰기 실패 → 크래시 루프)
    mkdir -p /var/lib/kafka
    chown -R 1000:1000 /var/lib/kafka
    docker run -d --name kafka --restart always \
      -p 9092:9092 \
      -v /var/lib/kafka:/var/lib/kafka/data \
      -e KAFKA_NODE_ID=1 \
      -e KAFKA_PROCESS_ROLES=broker,controller \
      -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
      -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
      -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${local.kafka_private_ip}:9092 \
      -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
      -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
      -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
      -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
      -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
      apache/kafka:3.8.0
  EOF

  # 새 AL2023 AMI 릴리스 때마다 인스턴스가 강제 교체되는 것 방지 (Kafka 데이터 보호)
  # AMI 업데이트는 의도적으로: terraform apply -replace=aws_instance.kafka
  lifecycle {
    ignore_changes = [ami]
  }

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  tags = { Name = "${var.project}-kafka-${var.env}" }
}
