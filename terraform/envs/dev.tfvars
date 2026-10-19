env = "dev"

kafka_instance_type = "t3.small"
db_instance_class   = "db.t4g.micro"

# 세 웹 앱의 로컬 dev 포트. 배포 도메인은 terraform locals가 붙이므로 여기엔 로컬 포트만 적는다.
frontend_origins = ["http://localhost:5175"] # 구매자 웹 dev

seller_frontend_origins = ["http://localhost:5173"] # 셀러센터 웹 dev

admin_frontend_origins = ["http://localhost:5174"] # 어드민 웹 dev
