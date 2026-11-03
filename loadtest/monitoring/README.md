# 검색 부하 측정 모니터링 스택

러너 호스트에서 지표를 모으고 Grafana로 패널을 보고 캡처한다. 도구는 팀 스택 그대로 Prometheus, Grafana, k6, CloudWatch Logs만 쓴다. 별도 로그 저장소(Loki, ELK)는 올리지 않는다.

## 서비스

compose 프로젝트 이름은 `aechak-perf-mon`이다.

| 서비스 | 이미지 | 역할 |
|---|---|---|
| prometheus | `prom/prometheus:v3.15.0` | 5초마다 수집, 보존 30일, k6 remote write 수신 |
| grafana | `grafana/grafana:13.2.4` | 데이터소스와 대시보드를 provisioning으로 올린다. 익명 접근 없음 |
| renderer | `grafana/grafana-image-renderer:v5.12.7` | 패널 PNG 렌더링 |
| mysqld-exporter | `prom/mysqld-exporter:v0.20.0` | 측정 중인 데이터셋의 RDS. Threads_running, 초당 쿼리, 버퍼 풀 읽기, processlist, 문장 다이제스트 |
| node-exporter | `prom/node-exporter:v1.12.1` | 러너 호스트 CPU와 메모리 |
| perf-mysqlcli | `mysql:8.4.10` | `sleep infinity`로 떠 있는 mysql 클라이언트. 러너의 SQL은 `docker exec`로 여기서 돈다 |

포트는 AWS에서 `127.0.0.1:3000`(Grafana)과 `127.0.0.1:9090`(Prometheus)이고 노트북에서는 `127.0.0.1:13000`과 `127.0.0.1:19090`이다. 렌더러와 exporter는 compose 네트워크 안에만 있다.

Prometheus 수집 대상은 아래와 같다. 라벨 `host`는 `api`, `runner`, `es` 중 하나다.

| job | 대상 |
|---|---|
| spring | api 호스트 8081 `/actuator/prometheus` (노트북 `host.docker.internal:18081`) |
| node | api 호스트 9100, 러너 node-exporter, ES 호스트 9100 |
| cadvisor | api 호스트 8088 (노트북에는 없음) |
| mysqld | mysqld-exporter (라벨 `dbid`, `dataset`) |
| es | ES 호스트 9114 (ES가 있을 때만) |
| prometheus | 자기 자신 |

## 대상과 비밀번호 만들기

compose를 띄우기 전에 `targets.sh`가 상태 디렉터리에 다음 파일을 쓴다. 상태 디렉터리는 AWS에서 `/opt/perf/state`, 노트북에서 `loadtest/monitoring/.local/`(git 제외)이다.

| 파일 | 내용 |
|---|---|
| `prom-targets/*.json` | job별 file SD 대상 |
| `mysqld-exporter.cnf` | 데이터셋 RDS 주소와 exporter 계정 |
| `grafana-admin` | Grafana admin 비밀번호. `capture.sh`가 읽는다 |
| `grafana.env` | 같은 비밀번호를 Grafana 환경변수로 |
| `renderer.env` | Grafana와 렌더러가 함께 쓰는 토큰 |
| `compose.env` | 노트북 전용. 상태 디렉터리와 포트를 바꾼다 |

AWS에서는 `outputs.json`에서 api, ES, RDS 주소를 읽고 exporter와 Grafana 비밀번호를 SSM에서 읽는다. 비밀번호는 영문, 숫자, `_.-`만 받는다.

```bash
# 러너 (root)
/opt/perf/kit/monitoring/targets.sh --aws /opt/perf/state/outputs.json --dataset 100k
docker compose -f /opt/perf/kit/monitoring/compose.yml up -d --force-recreate

# 노트북 (loadtest/search/compose.yml 앱 스택이 13306, 18081에 떠 있을 때)
loadtest/monitoring/targets.sh --local
docker compose -f loadtest/monitoring/compose.yml --env-file loadtest/monitoring/.local/compose.env up -d
```

kit-pull은 kit 디렉터리를 통째로 교체하므로 kit을 새로 받은 뒤 스택을 다시 띄울 때는 `--force-recreate`를 붙인다. Prometheus와 Grafana 데이터는 볼륨에 남는다. 데이터셋만 바꿀 때는 `targets.sh`를 다시 돌리고 `mysqld-exporter`만 다시 만든다.

```bash
docker compose -f /opt/perf/kit/monitoring/compose.yml up -d --force-recreate mysqld-exporter
```

내릴 때는 `docker compose -f ... down`이고 지표까지 지우려면 `-v`를 붙인다.

## Grafana 열기 (SSM 포트 포워딩)

Grafana와 Prometheus는 러너의 127.0.0.1에만 열려 있어 SSM 포트 포워딩으로 연다. `make grafana`와 `make prom`이 같은 일을 한다.

```bash
aws ssm start-session --profile aechak --region ap-northeast-2 \
  --target <러너 instance_id> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["13000"]}'
```

브라우저에서 `http://localhost:13000`을 열고 `admin`과 SSM `/aechak-perf/grafana/admin-password` 값으로 로그인한다. Prometheus는 `portNumber` 9090, `localPortNumber` 19090으로 같은 명령을 쓴다.

## 대시보드

| uid | 내용 |
|---|---|
| `aechak-search-perf` | 캡처용 대시보드. 변수 `dbid`(RDS 인스턴스 id), `testid`(k6 run id, 정규식) |
| `mysql-overview` | grafana.com 7362 리비전 5 |
| `node-exporter-full` | grafana.com 1860 리비전 45 |
| `jvm-micrometer` | grafana.com 4701 리비전 10 |
| `k6-prometheus` | grafana.com 19665 리비전 3 |
| `cadvisor-exporter` | grafana.com 14282 리비전 1 |

커뮤니티 대시보드는 grafana.com API에서 받은 JSON의 데이터소스를 provisioning한 uid(`prometheus`)로 바꿔 저장했다.

`aechak-search-perf` 패널 id와 캡처 파일 이름은 고정이다.

| id | 파일 slug | 데이터 |
|---|---|---|
| 1 | rds-cpu | CloudWatch RDS CPUUtilization |
| 2 | rds-connections | CloudWatch DatabaseConnections |
| 3 | mysql-threads | Threads_running, Threads_connected |
| 4 | mysql-qps | Queries, Com_select 초당 |
| 5 | innodb-disk-reads | Innodb_buffer_pool_reads, read_requests 초당 |
| 6 | api-cpu | cAdvisor api 컨테이너, JVM process_cpu_usage, api 호스트 |
| 7 | api-memory-heap | 컨테이너 working set, JVM 힙 |
| 8 | hikari-pool | 활성, 유휴, 대기, 최대 |
| 9 | hikari-acquire-timeout | 획득 시간 평균과 최대, 타임아웃 건수 |
| 10 | http-server-latency | 검색 엔드포인트 서버 측 p95, p99, 평균 (500ms 선) |
| 11 | k6-rate | k6 초당 요청, kind별 |
| 12 | k6-latency | k6 첫 페이지 p95, p50, p95, p99, 최대 (500ms 선) |
| 13 | k6-errors | 실패 status별 초당 건수, 실패율, dropped_iterations |
| 14 | runner-cpu | 러너 CPU |
| 15 | es-cpu-heap | ES CPU와 힙 |
| 16 | es-search-latency | ES 쿼리당 평균 지연 |
| 17 | api-logs | CloudWatch Logs Insights `/perf/aechak-api`의 ERROR, WARN, Hikari, timeout 줄 |
| 18 | slow-log | CloudWatch Logs Insights `/aws/rds/instance/$dbid/slowquery` 10초 단위 건수와 최대 시간 |
| 19 | rds-credits | CPUCreditBalance, CPUSurplusCreditBalance |
| 20 | rds-iops-memory | ReadIOPS, WriteIOPS, FreeableMemory |

기본 캡처 집합은 1부터 14, 17, 18, 20이다. 운영 거울층은 19를, ES 단계는 15와 16을 더한다.

k6 패널은 k6가 remote write로 보낸 지표를 `testid` 라벨로 거른다. k6 분위수(`k6_http_req_duration_p95` 등)는 run 시작부터 누적한 값이라 시간에 따른 변화는 패널 10의 서버 측 히스토그램으로 본다. k6 쪽 실행 옵션은 아래와 같다.

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),max' \
k6 run -o experimental-prometheus-rw --tag testid=<run-id> \
  --system-tags status,method,name,group,check,error_code,expected_response,scenario \
  search.js
```

`--system-tags`에서 `url`을 빼야 검색어와 커서마다 시계열이 새로 생기지 않는다.

## 캡처

`loadtest/search/capture.sh`가 Grafana render API로 run 시간 범위의 패널을 PNG로 저장한다.

```bash
capture.sh --run-id <run-id> --from <시작 ms> --to <끝 ms> [--dbid aechak-perf-100k] [--panels "1 2 ..."] [--out DIR] [--grafana URL]
```

- 범위를 앞뒤로 30초씩 넓히고 `var-testid`에 run id, `var-dbid`에 `--dbid`를 넣는다.
- 패널마다 `NN-slug.png`(1200x500)와 대시보드 전체 `00-dashboard.png`를 쓴다.
- admin 비밀번호는 러너에서 `/opt/perf/state/grafana-admin`, 노트북에서 `loadtest/monitoring/.local/grafana-admin`을 읽는다. 러너 여부는 `/opt/perf/state/role` 파일로 정하고 `PERF_ENV=local`이면 노트북으로 본다.
- 기본 출력은 러너 `/opt/perf/results/captures/<run-id>/`, 노트북 `loadtest/search/.perf/local/captures/<run-id>/`이다.
- 일부 패널이 실패해도 0으로 끝나고 마지막 줄 `CAPTURE_DONE run_id=... ok=N failed=<id 목록 또는 none> out=...`에 실패한 패널을 적는다.

노트북에는 AWS 자격 증명이 없어 CloudWatch 패널(1, 2, 17, 18, 19, 20)은 빈 패널로 렌더링된다.
