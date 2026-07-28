# 앱이 부팅 시 /aechak/{env}/api/ 아래를 통째로 읽어 yml의 플레이스홀더로 매핑한다.
# taskdef에 값을 박으면 엔드포인트가 바뀔 때마다 리비전 재배포가 필요해져서 SSM을 거친다.
#
# 여기 있는 값은 application-dev.yml에 기본값이 없어 하나라도 빠지면 앱이 기동에 실패한다.
# 반대로 카카오 키·JWT 키는 여기 없다 — terraform이 값을 알 수 없고, 없어도 앱은 뜬다(로그인만 비활성).

resource "aws_ssm_parameter" "db_url" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.project}"
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_USERNAME"
  type  = "String"
  value = "app"
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "redis_host" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATA_REDIS_HOST"
  type  = "String"
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

resource "aws_ssm_parameter" "redis_port" {
  name  = "/${var.project}/${var.env}/api/SPRING_DATA_REDIS_PORT"
  type  = "String"
  value = "6379"
}
