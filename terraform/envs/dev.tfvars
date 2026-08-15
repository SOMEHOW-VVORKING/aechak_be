env = "dev"

kafka_instance_type = "t3.small"
db_instance_class   = "db.t4g.micro"

frontend_origins = ["http://localhost:5175", "http://localhost:5173"]

seller_frontend_origins = ["http://localhost:5174"] # 셀러센터 웹 dev — FE 리포 포트 확정 시 갱신
