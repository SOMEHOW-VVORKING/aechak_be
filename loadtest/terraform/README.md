# perf 스택 terraform

검색 부하 측정 전용 AWS 스택이다. 팀 `terraform/` 루트와 따로 둔 별도 루트이고 팀 VPC와 서브넷은 data source로 읽기만 한다.

## 만드는 것

| 역할 | 리소스 | 비고 |
|---|---|---|
| runner | EC2 `c7g.large` arm64, gp3 30GB | k6 v2.2.0, 모니터링 compose |
| api | EC2 `m7i.large` x86_64, gp3 20GB | api 컨테이너, redis, kafka, exporter |
| es | EC2 `m7g.large` arm64, gp3 40GB | `es_enabled = true`일 때만 |
| db | RDS MySQL 8.4.11 `db.m7g.large`, gp3 20GB | 데이터 크기마다 하나. `aechak-perf-100k`, `aechak-perf-1m` |
| mirror | RDS `db.t4g.micro` | `mirror_snapshot_id`를 줄 때만. 스냅샷에서 복원 |

- EC2는 전부 `aechak-public-a`에 퍼블릭 IP로 두고 SSM으로만 접속한다. 22번 포트는 없다. IMDSv2 필수, hop 2라서 컨테이너도 인스턴스 역할을 쓴다.
- RDS는 `aechak-data-a`, `aechak-data-c` 위의 자체 서브넷 그룹을 쓰고 EC2와 같은 AZ(ap-northeast-2a)에 둔다. Single-AZ, 암호화, 자동 백업 없음(수동 스냅샷만), 마이너 버전 자동 업그레이드 끔.
- 파라미터 그룹 `aechak-perf-mysql84`. 팀 값(utf8mb4, `utf8mb4_0900_ai_ci`, Asia/Seoul)에 `performance_schema=1`, `max_digest_length`와 `performance_schema_max_digest_length`와 `performance_schema_max_sql_text_length` 4096, `slow_query_log=1`, `long_query_time=0.1`, `log_output=FILE`을 더했다. 앞의 네 값은 static이라 그룹을 바꾸면 재부팅해야 적용된다.
- `events_statements_history_long` consumer는 RDS 파라미터로 켤 수 없다. 재부팅마다 꺼진 상태로 돌아오므로 `explain.sh`가 시작할 때 `performance_schema.setup_consumers`를 갱신해 켠다.
- 측정층 RDS는 Performance Insights 7일 보존(Database Insights 표준 모드 무료 범위)과 Enhanced Monitoring 15초를 켠다. slowquery 로그는 CloudWatch Logs `/aws/rds/instance/<id>/slowquery`로 내보내고 보존 14일이다.
- 보안 그룹. api 8080, 8081, 9100, 8088은 runner에서만. RDS 3306은 api와 runner에서만. es 9200은 api와 runner에서, 9114와 9100은 runner에서만. 인터넷에서 들어오는 규칙은 없다.
- S3 `aechak-perf-artifacts-447170313132`(퍼블릭 차단, SSE-S3, 버전 관리 없음, destroy 때 객체째 삭제).
- SSM SecureString `/aechak-perf/db/master-password`, `/aechak-perf/db/exporter-password`, `/aechak-perf/grafana/admin-password`. 값은 `random_password`로 만든다.
- CloudWatch Logs `/perf/aechak-api`(api 컨테이너 awslogs, 보존 14일).
- 호스트 역할마다 IAM 역할 하나. 전부 `AmazonSSMManagedInstanceCore`를 붙이고 필요한 S3 경로, SSM 파라미터, 로그 그룹, 지표 조회 권한만 더했다. api 역할이 읽는 SSM 파라미터는 DB 마스터 비밀번호 하나다.

user_data는 첫 부팅에 docker, docker compose v5.6.0, jq, openssl, python3, time(runner는 k6도)을 설치하고 `/opt/perf/` 디렉터리, `/opt/perf/state/{bucket,role}`, `/opt/perf/bin/kit-pull`, `/etc/profile.d/perf.sh`를 만든다. 내려받는 바이너리는 sha256으로 확인한다. curl은 AL2023 기본 curl-minimal을 쓴다(curl 패키지는 충돌한다). es 호스트는 Elasticsearch가 요구하는 `vm.max_map_count=262144`를 바로 적용하고 `/etc/sysctl.d`에 남겨 재부팅 뒤에도 유지한다. AMI와 user_data는 `ignore_changes`라 새 AL2023 AMI가 나와도 인스턴스를 다시 만들지 않는다.

## 팀 루트와 분리

- state는 팀과 같은 버킷 `aechak-tfstate-447170313132`의 key `perf/terraform.tfstate`에 둔다. 팀 루트는 `main/terraform.tfstate`라서 서로의 plan에 나타나지 않는다.
- 리소스 이름은 전부 `aechak-perf-`로 시작하고 태그는 `Project=aechak-perf`, `ManagedBy=terraform`이다. 비용 탐색기에서 Project 태그로 나눠 볼 수 있다.
- 계정 가드(`allowed_account_ids`)는 팀 루트와 같다.

## 사용법

`aws sso login --profile aechak` 뒤에 이 디렉터리에서 실행한다. 변수는 전부 기본값이 있어서 tfvars 없이 돈다.

```bash
export AWS_PROFILE=aechak
terraform init
terraform plan -out perf.tfplan
terraform apply perf.tfplan
cd ../search && make outputs   # terraform output -json을 S3 state/outputs.json에 올린다
```

- ES 호스트는 `-var es_enabled=true`, 저사양 ES 셀은 `-var es_instance_type=t4g.small`을 더한다.
- 운영 거울층은 `-var mirror_snapshot_id=aechak-perf-100k-baseline`처럼 스냅샷을 준다. 다 쓰면 변수를 빼고 다시 apply해 지운다.
- 전체 삭제는 `terraform destroy`다. 수동 스냅샷은 terraform이 만든 것이 아니라서 남는다.

`perf.tfplan`에는 비밀번호가 평문으로 들어 있으니 커밋하지 않는다(`.gitignore` 대상).

## 켜고 끄기

terraform은 리소스를 만들고 지우기만 한다. 세션 단위로 켜고 끄는 일은 `loadtest/search`의 Makefile이 한다.

- `make up DATASET=100k|1m|mirror`. runner, api와 그 데이터셋의 RDS를 시작하고 다른 perf RDS는 멈춘다. 준비되면 두 호스트에서 kit-pull을 돌리고 runner에서 모니터링 compose를 띄운다.
- `make down`. perf EC2와 perf RDS를 전부 멈춘다.

멈춘 동안에도 EBS와 RDS 스토리지 요금은 붙는다. 퍼블릭 IP는 멈추면 반납되어 요금이 없다. RDS는 멈춘 지 7일 뒤 자동으로 다시 켜진다. 아래 자동 정지가 다음 05:00에 다시 멈추지만 그 사이 요금이 붙으므로 길게 쉴 때는 스냅샷을 뜨고 인스턴스를 지운다.

## 자동 정지

`make down`을 잊어도 밤새 켜져 있지 않도록 EventBridge Scheduler가 매일 05:00(Asia/Seoul)에 perf 스택을 멈춘다.

- 측정층 RDS마다 스케줄 하나(`aechak-perf-100k-stop`, `aechak-perf-1m-stop`, 거울층이 있으면 `aechak-perf-mirror-stop`)가 `aws-sdk:rds:stopDBInstance`를 부른다.
- 스케줄 `aechak-perf-ec2-stop`이 `aws-sdk:ec2:stopInstances`로 perf EC2를 한 번에 멈춘다.
- 전용 IAM 역할 `aechak-perf-auto-stop`은 이 RDS와 EC2의 ARN에만 정지 권한이 있다. 재시도는 0회이고 유연한 시간 창은 끈다.
- 이미 멈춘 RDS에 보낸 호출은 실패하고 끝난다. 재시도가 없어서 다른 영향은 없다.
- 측정 세션이 05:00을 넘길 것 같으면 `-var 'auto_stop_cron=cron(0 7 * * ? *)'`처럼 cron 식을 주고 apply한다. 시간대는 Asia/Seoul로 고정이다.
- 끄려면 `-var auto_stop_enabled=false`로 apply한다. 스케줄과 역할이 함께 지워진다.

대상 ARN과 입력 형식은 AWS 문서 [Using universal targets in EventBridge Scheduler](https://docs.aws.amazon.com/scheduler/latest/UserGuide/managing-targets-universal.html)를 따랐다. 대상 ARN은 `arn:aws:scheduler:::aws-sdk:<서비스>:<API 이름>`이고 입력은 그 API의 요청 파라미터 JSON이다. RDS 입력 키는 Step Functions SDK 통합과 같은 표기인 `DbInstanceIdentifier`를 쓴다.

## 비용

서울 리전 온디맨드. AWS 공개 요금표 CSV(`https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/<서비스>/current/ap-northeast-2/index.csv`)에서 2026-11-03에 읽은 값이다. 계정 SCP가 pricing API를 막아 CSV를 썼다.

| 항목 | 단가(USD) | 요금표 행 |
|---|---|---|
| RDS `db.m7g.large` | 0.2344 / 시간 | $ 0.2344 per RDS db.m7g.large Single-AZ instance hour (or partial hour) running MySQL |
| EC2 `m7i.large` (api) | 0.1239 / 시간 | $0.1239 per On Demand Linux m7i.large Instance Hour |
| EC2 `c7g.large` (runner) | 0.0816 / 시간 | $0.0816 per On Demand Linux c7g.large Instance Hour |
| EC2 `m7g.large` (es) | 0.1003 / 시간 | $0.1003 per On Demand Linux m7g.large Instance Hour |
| EC2 `t4g.small` (저사양 es) | 0.0208 / 시간 | $0.0208 per On Demand Linux t4g.small Instance Hour |
| RDS `db.t4g.micro` (mirror) | 0.025 / 시간 | $ 0.025 per RDS db.t4g.micro Single-AZ instance hour (or partial hour) running MySQL |
| RDS T4g CPU 크레딧 초과분 | 0.075 / vCPU 시간 | USD 0.075 per vCPU-Hour of T4g CPU Credits (MySQL) |
| 퍼블릭 IPv4 | 0.005 / 시간 / 개 | $0.005 per In-use public IPv4 address per hour |
| RDS gp3 | 0.131 / GB 월 | $0.131 per GB-month provisioned GP3 storage running MySQL |
| EBS gp3 | 0.0912 / GB 월 | $0.0912 per GB-month of General Purpose (gp3) provisioned storage - Asia Pacific (Seoul) |
| RDS 스냅샷 | 0.095 / GB 월 | $0.095 per additional GB-month backup storage exceeding free allocation running MySQL |
| CloudWatch Logs 적재 | 0.76 / GB | $0.76 per GB custom log data ingested in Standard log class - Asia Pacific (Seoul) |
| S3 Standard | 0.025 / GB 월 | $0.025 per GB - first 50 TB / month of storage used |
| EventBridge Scheduler | 0 (월 1,400만 건까지) | $0 for the first 14 million scheduled invocations. 넘으면 $1.15 per million scheduled invocations in Asia Pacific (Seoul). 서비스 코드 AWSEvents |
| Performance Insights 7일 | 0 | 무료 행은 요금표에 없다. 유료 행은 7일을 넘는 보존(0.002405 / vCPU 시간)과 Advanced 모드(0.0125 / vCPU 시간)다 |

| 묶음 | 금액(USD) |
|---|---|
| 측정층 시간당(RDS, api, runner, IPv4 둘) | 0.45 |
| ES 포함 시간당(IPv4 하나 추가) | 0.56 |
| 운영 거울층 시간당(mirror, api, runner, IPv4 둘. 크레딧 초과분 별도) | 0.24 |
| 보관 월(RDS gp3 20GB 둘 5.24, EBS 50GB 4.56) | 9.80 |
| ES 볼륨이 있을 때 보관 월(EBS 90GB 8.21) | 13.45 |
| 세션 50시간(ES 없이 30시간 13.50, ES 포함 20시간 11.10) | 24.60 |

시리즈 두 달이면 세션 약 25달러, 운영 거울층 약 1달러, 보관 약 25달러, 로그와 S3와 스냅샷 몇 달러로 약 55달러다. CloudWatch Logs 적재는 계정 전체에 월 5GB 무료가 있고 RDS 스냅샷은 리전의 프로비저닝 스토리지 크기만큼 무료라서 실제 청구는 더 작을 수 있다.
