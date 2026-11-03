# SEARCH-T00 측정 kit와 perf 스택

2026-11-04에 AWS perf 스택에서 잰 결과다. 대상 rev는 `6dce8608b83c-dirty-5c874761`(origin/develop 6dce8608 위에 T00의 앱 변경을 더한 미커밋 트리)이고 첫 run은 kit 버전 1, 확인 run은 kit 버전 2로 돌렸다. 노트북 수치는 기록하지 않는다.

## 요약

- `make variant` 한 번으로 10만 건 전체 셀(warmup, smoke, ramp, steady 3회, spike, sequential, explain)이 수동 단계 없이 돌았다. run마다 raw 파일(k6 요약 JSON과 텍스트, CloudWatch csv, 다이제스트, slow log, env.txt, ramp tsv, spike points, explain)과 Grafana 캡처와 요약 표(`results/SEARCH-T00.md`)가 남았다.
- 잡음 폭. steady 3회의 첫 페이지 p95는 742ms(740에서 744ms)이고 폭은 중앙값의 0.6%다. 끝 조건(15% 이하)을 만족한다. 비교 부하 1.4 req/s의 낮은 부하에서 잰 폭이라 포화에 가까운 부하의 폭은 T01에서 다시 본다.
- 기준선은 10만 건에서도 SLO(첫 페이지 p95 500ms 미만)를 어느 부하에서도 지키지 못했다. 0.25 req/s에서도 첫 페이지 p95가 720ms였다. 최대 안정 요청률은 0(SLO 미충족)이고 steady와 spike는 비교 부하를 기준으로 돌았다.
- 첫 run에서 kit 결함 둘이 드러나 kit 버전 2에서 고쳤다(아래 kit 버전 2 절).
- kit 버전 2 확인 run(ramp와 spike만)에서 ramp는 2 req/s(SLO 실패, 처리 기준 통과)에서 4 req/s로 올라간 뒤 과부하(실제 3.14 req/s, 첫 페이지 p95 5.5초, dropped 7)에서 멈췄다. 처리 가능 요청률은 2 req/s이고 4에서 실패했다. 바닥 단계는 돌지 않았다.

## perf 스택

| 역할 | 리소스 | 설정 |
|---|---|---|
| 측정 DB | RDS MySQL 8.4.11 `db.m7g.large` 두 대(`aechak-perf-100k`, `aechak-perf-1m`) | gp3 20GB, ap-northeast-2a, 퍼블릭 접근 없음, 백업 보존 0, Performance Insights 7일, Enhanced Monitoring 15초, slow log를 CloudWatch Logs로 |
| api 호스트 | EC2 `m7i.large` | api 컨테이너 1 vCPU 2GB(dev Fargate와 같음), redis, kafka, node_exporter, cAdvisor |
| 러너 | EC2 `c7g.large` | k6 v2.2.0, Prometheus, Grafana, image renderer, mysqld_exporter |
| 그 밖 | S3 버킷, 보안 그룹 넷, IAM 역할, SSM 파라미터 셋, 로그 그룹 셋 | 팀 VPC와 서브넷은 읽기만 하고 state는 `perf/terraform.tfstate`에 따로 둔다 |
| 자동 정지 | EventBridge Scheduler 셋 | 매일 05:00(KST)에 perf RDS와 EC2를 멈춘다 |

`terraform apply`로 리소스 37개를 만드는 데 8분 25초가 걸렸다(RDS 생성 8분). 계정 SCP는 RDS 생성을 막지 않았다.

비용은 서울 리전 온디맨드 공개 요금표 CSV(EC2 2026-10-29, RDS 2026-10-07, VPC와 S3 2026-11-03, AWSEvents 2026-09-24 게시본)로 계산했다.

| 항목 | 금액 |
|---|---|
| 측정 세션(RDS 한 대, api, 러너, 퍼블릭 IPv4 둘) | 시간당 0.45달러 |
| ES 호스트 포함 | 시간당 0.56달러 |
| 운영 거울층 세션(`db.t4g.micro`, api, 러너) | 시간당 0.24달러와 크레딧 초과분 |
| 멈춘 동안 저장(RDS gp3 20GB 둘, EBS 50GB) | 월 9.80달러 |
| 자동 정지 스케줄 | 0달러(월 1,400만 회 호출까지 무료) |

이번 T00 세션(apply 00:39부터 `make down` 02:34까지)은 RDS 약 1.9시간, EC2 두 대 약 1.8시간으로 약 1달러다. 기준 스냅샷 둘(20GB씩)은 세션 사이에도 남는다.

## RDS 파라미터

perf 파라미터 그룹은 인스턴스를 만들 때 붙어서 재부팅 없이 적용됐다(`in-sync`). `performance_schema` 1, `max_digest_length`와 `performance_schema_max_digest_length`와 `performance_schema_max_sql_text_length` 4096, `slow_query_log` 1, `long_query_time` 0.1, `log_output` FILE, `character_set_server` utf8mb4, `collation_server` utf8mb4_0900_ai_ci, `time_zone` Asia/Seoul이다. run의 env.txt에도 엔진 8.4.11, 버퍼 풀 5.25GiB, 다이제스트 길이 4096, `long_query_time` 0.1이 찍혔다. `events_statements_history_long` consumer는 RDS 파라미터가 없어 `explain.sh`가 실행 때 켠다. explain run에서 바인딩된 문장 45개 블록을 얻었다.

## 시드와 스냅샷

| 크기 | 첫 부팅 | 시드 | manifest | 기준 스냅샷 |
|---|---|---|---|---|
| 10만 건 | 약 1분 | 4초 | 3초 | 3분(`aechak-perf-100k-baseline`) |
| 100만 건 | 약 1분 | 30초 | 32초 | 3분(`aechak-perf-1m-baseline`) |

## 셀별 소요 시간(10만 건 기준선, kit 버전 1)

| 셀 | 실행 | 비고 |
|---|---|---|
| warmup | 61초 | |
| smoke | 30초 | |
| ramp | 274초 | 2 req/s 뒤 바닥 단계 1, 0.5, 0.25와 그 앞 유휴 대기 |
| steady 3회 | 181초씩 | |
| spike | 121초 | burst 30초, probe 90초 |
| sequential | 431초 | 요청 1,020건, 건당 0.4초에서 0.7초 |
| explain | 40초 | |
| run 사이 유휴 대기 | 약 34초씩 8번 | |
| 변형 시작 | 약 1분 | api 재시작, warm set, RDS CPU 유휴 확인 |
| 변형 끝 | 7.7분 | CloudWatch 대기와 재수집, 캡처(전체 패널 run 하나에 61초, 작은 묶음 15초에서 20초) |

변형 하나의 벽시계는 39.5분이었다. 기준선은 sequential이 느려서 목표로 잡은 25분에서 30분보다 길었다. sequential은 요청을 차례로 보내므로 응답이 빨라지면 함께 짧아진다.

## 버전

- MySQL 8.4.11(RDS), Hibernate 7.2.7.Final, QueryDSL 7.0, Flyway 11.14.1. Flyway를 돌리는 boot 모듈은 api뿐이다(seller-api, batch, admin의 runtimeClasspath에 Flyway가 없다).
- Boot 4.0.5 BOM의 `elasticsearch-java`는 9.2.6이고 api jar에는 아직 없다. Elasticsearch 자동 설정은 `spring-boot-starter-elasticsearch`(또는 data 스타터)가 가져온다.
- k6 v2.2.0, Prometheus v3.15.0, Grafana 13.2.4, grafana-image-renderer v5.12.7, mysqld-exporter v0.20.0, node-exporter v1.12.1, docker compose v5.6.0.

## 스파이크 판정

- A. FunctionContributor `match_against`. 통과. QueryDSL의 `function('match_against', ...)`이 `match(name) against (? in boolean mode)`로 렌더링되고 MySQL 8.4 ngram FULLTEXT에서 동작한다. 100만 건 사본에서 `ALTER TABLE products ADD FULLTEXT INDEX ... WITH PARSER ngram`은 49초, 행 복사는 16초였다. `ngram_token_size`는 2, `innodb_ft_enable_stopword`는 1이다. 강아지 500,000, 사료 100,000, 관절 33,334, 한정판 1,000으로 MATCH만, MATCH와 LIKE, LIKE만의 결과 수가 모두 같았다. 시드 상품명에는 띄어 쓴 ngram 오탐이 없고 LIKE 재검사는 실제 데이터를 위한 안전장치다. T04는 contributor 경로로 간다.
- B. `jakarta.persistence.sql-load-script-source`. 동작하지만 쓰지 않는다. 값은 파일 이름만 써야 하고 `classpath:`를 붙이면 Hibernate 7.2.7이 경고만 남기고 건너뛴다. `IntegrationTestBase`와 `KafkaIntegrationTestBase`에서 ddl-auto=create 뒤에 돌고 TRUNCATE 뒤에도 인덱스가 남는다. 그러나 schema-generation create 대상 파일(V27, V29, V34, V58 테스트의 엔티티 DDL)에도 ALTER가 덧붙어 마이그레이션 테스트의 이전 상태를 오염시킨다. T04는 `IntegrationTestConfig`에서 JdbcTemplate로 한 번 ALTER하는 폴백으로 간다.
- C. `count(*) over()`. 값은 별도 COUNT와 같다(첫 페이지, 필터 프리셋 다섯). AWS EXPLAIN ANALYZE(두 번째 실행)에서 윈도는 일치 행 전부를 임시 테이블에 넣은 뒤 LIMIT을 적용한다. 그래서 이득은 일치율이 낮은 검색어에서만 난다. T05는 등급별로 이득을 기록하고 고일치 등급은 별도 COUNT와 같다고 본다.

| 크기 | 검색어(일치율) | 목록 + 별도 COUNT | 윈도 COUNT |
|---|---|---|---|
| 10만 건 | 강아지(47%) | 510ms + 345ms | 862ms |
| 10만 건 | 관절(약 3%) | 약 350ms + 320ms | 약 358ms |
| 100만 건 | 강아지(50%) | 5.6초 + 3.9초 | 9.3초 |
| 100만 건 | 관절(약 3%) | 3.3초 + 3.2초 | 3.4초 |

- D. Elasticsearch 클라이언트. 통과하되 매핑은 바꾼다. 9.2.6 클라이언트가 9.2.6 서버에 색인 생성, 문서 색인, `match_phrase`, `search_after`, count를 보냈다. 스파이크의 매핑(ngram 1에서 3, letter와 digit)은 2026-11-04에 ES 9.2.6에서 다시 확인해 보니 단어 중간 부분 문자열을 놓쳤다(`강아`로 `강아지 연어 사료`, `사료`로 `사료통`, `강아지`로 `큰강아지들 간식`을 못 찾음). 길이가 다른 gram마다 position이 1씩 올라 phrase 위치가 어긋나기 때문이다. gram 길이를 2로 고정하고 공백과 문장부호를 남긴 매핑은 같은 문서와 검색어에서 LIKE와 결과가 같았다. T07은 이 매핑과 1글자 검색어 별도 처리로 간다. nori 이미지는 T08 시연용으로 빌드만 확인했다.

## 기준선에서 본 것(T01에서 정식으로 잰다)

- 실행 계획은 두 크기 모두 sellers를 전체 스캔한 뒤 `ix_products_seller_id`로 products를 찾는다. 2026-10-15 노트북 기준은 categories부터 읽었다.
- sequential 첫 페이지 중앙값은 none과 rare 약 480ms, low 약 500ms, mid 약 540ms, high 약 735ms이고 둘째 페이지(COUNT 없음)는 216ms에서 419ms다. 첫 페이지의 절반쯤이 COUNT다.
- steady 1.4 req/s에서 RDS CPU는 25%에서 35%, 요청당 RDS CPU는 0.39초에서 0.48초였다.
- spike(6 req/s, 30초)에서 burst 실패율 56%(모두 클라이언트 10초 타임아웃), Hikari 대기 최대 44, 서버 HTTP p95 최대 22.6초였다. 클라이언트가 끊은 뒤에도 서버가 계속 처리했다. probe 기준으로 31초 뒤 회복했다. 이 run은 kit 버전 1의 VU 부족으로 반복 5건이 빠져 부하 미달로 표시됐다.

## kit 버전 2

첫 run에서 드러난 결함을 고친 버전이다. 첫 run의 수치는 버전 1로 남기고 단계 비교에는 쓰지 않는다.

- ramp. 버전 1은 첫 페이지 p95가 500ms를 넘는 단계에서 바로 멈추고 바닥 단계(1, 0.5, 0.25)로 내려갔다. 지연 때문에만 SLO를 못 넘는 기준선에서 바닥 단계가 3분을 썼고 처리 가능 요청률은 2보다 위를 찾지 못했다. 버전 2는 실패율 1% 미만, dropped 0, k6 정상 종료가 이어지는 동안 계속 올리고 바닥 단계는 첫 단계부터 과부하일 때만 쓴다.
- spike VU. 혼합 반복 하나는 첫 페이지와 다음 페이지 최대 둘, 모두 세 번의 요청을 차례로 보낸다. 버전 1은 VU를 요청률 x 타임아웃 x 1.2로 잡아 6 req/s에서 72였다. 버전 2는 반복률 x 반복당 최대 요청 수 x 타임아웃 x 1.2로 잡아 155다. 확인 run의 burst에서는 빠진 반복이 없었다. 남은 5건은 burst 직후 probe가 VU를 5개만 미리 만들어 둔 탓이었다. probe도 VU를 미리 다 만들도록 고쳤고 `k6 inspect`로 burst 155, probe 12를 확인했다(AWS 확인은 T01 첫 run에서).
- 러너 로그 시각을 KST로 맞췄다.

## 남은 확인

- api 로그 패널은 이 run 동안 ERROR와 WARN 로그가 실제로 없어서 값이 있는 경우를 아직 보지 못했다.
- `CPUCreditBalance` 패널은 운영 거울층(T01)에서, ES 패널은 T07에서 확인한다.
- 버퍼 풀 디스크 읽기가 warmup 2, ramp 24, steady 1회차 14, explain 2건 나왔다. RDS를 다시 켠 직후 warm set 밖의 페이지로 보이며 I/O 관측으로만 적는다.

## 캡처

- `captures/2026-11-04-SEARCH-T00-100k-spike-base-r1/00-dashboard.png`. spike 동안의 전체 대시보드다. burst에서 Hikari 대기가 44까지 쌓이고 서버 p95가 22.6초까지 오른 뒤 probe 구간에 내려온다.
- `captures/2026-11-04-SEARCH-T00-100k-ramp-kit2-r1/12-k6-latency.png`. kit 버전 2 ramp의 k6 지연이다. 2 req/s에서 0.8초 안팎이던 지연이 4 req/s에서 4초에서 6.6초로 오른다.

나머지 run의 패널 캡처는 perf 버킷의 `results/<run-id>/captures/`에 있다.
