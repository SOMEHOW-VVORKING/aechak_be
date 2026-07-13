# ElastiCache Redis
# 현재는 단일 노드, 복제 없음 — 캐시/락 용도이므로,, MVP 판단. 추후 확장 가능
# 참고: Valkey 엔진으로 바꾸면 ~20% 저렴 (redis 호환 포크) — 비용 필요 시 검토

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project}-${var.env}"
  subnet_ids = [aws_subnet.data_a.id, aws_subnet.data_c.id] # 2-AZ 요건
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id      = "${var.project}-${var.env}"
  engine          = "redis"
  engine_version  = "7.1"
  node_type       = "cache.t4g.micro"
  num_cache_nodes = 1
  port            = 6379

  parameter_group_name = "default.redis7"
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  tags = { Name = "${var.project}-${var.env}" }
}

output "redis_endpoint" {
  value = "${aws_elasticache_cluster.redis.cache_nodes[0].address}:6379"
}
