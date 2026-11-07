# 검색 부하 측정 kit

키워드 상품 검색(`GET /api/v1/search/products`)의 처리 한계와 병목을 재는 kit다. 검색 성능 개선 시리즈(SEARCH-T00부터 T10)의 모든 단계가 같은 방식으로 재고 같은 표와 같은 Grafana 캡처를 남기도록 측정 규약을 코드로 고정한다. 수치는 AWS의 전용 perf 스택에서만 낸다. dev 환경을 쓰면 팀이 쓰는 DB에 상품 100만 건을 넣고 CPU를 포화시켜야 하고, develop이 머지될 때마다 api가 다시 배포되어 before와 after를 같은 조건에서 잴 수 없다.

규약(셀 집합, 반복, 인스턴스, Hikari, 인스턴스 수명 주기, manifest, 캡처 목록)을 바꾸면 `lib/common.sh`의 `KIT_VERSION`을 올린다. 이 값은 env.txt마다 `kit_version`으로 남고, 버전이 다른 결과끼리는 비교하지 않는다. 버전 2에서 바뀐 점은 셋이다. ramp는 SLO에 실패한 뒤에도 처리 기준을 지키는 동안 계속 올라가 처리 가능 요청률을 찾는다. 바닥 단계는 첫 단계부터 처리 기준을 못 지킬 때만 돈다. VU 상한은 반복 하나가 차례로 보낼 수 있는 최대 요청 수로 잡는다. 버전 1은 SEARCH-T00의 첫 AWS 측정에만 썼고 버전 2는 SEARCH-T01에 썼다. 버전 3에서 바뀐 점은 셋이다. ramp는 첫 단계가 SLO에 실패하면 처리 기준을 지켰더라도 바닥 단계를 내려가며 SLO를 지키는 요청률을 찾고 바닥 단계는 반복이 20회 이상 들도록 늘린다. 검색어, 필터, 요청 흐름을 반복 순번으로 정한다. 변형을 시작할 때 `events_statements_history_long` consumer를 끄고 꺼진 것을 확인하지 못하면 멈춰서 before와 after의 관측 설정을 맞춘다.

## 두 층

| 층 | 구성 | 쓰는 단계 |
|---|---|---|
| 측정층 | RDS `db.m7g.large`(데이터 크기마다 하나, `aechak-perf-100k`와 `aechak-perf-1m`), api 호스트 EC2 `m7i.large`(api 컨테이너는 dev 태스크와 같은 1 vCPU, 2GB), 러너 EC2 `c7g.large`(k6와 모니터링), T07부터 ES 호스트 `m7g.large` | 모든 단계의 수치 |
| 운영 거울층 | 10만 건 기준 스냅샷을 dev와 prod가 쓰는 `db.t4g.micro`로 복원한 `aechak-perf-mirror`. 나머지는 측정층과 같고 manifest는 10만 건 것과 비교한다 | T01과 T10만 |

측정층은 버스트 크레딧이 없는 고정 성능 인스턴스라서 앞 측정이 쓴 크레딧이 뒤 측정에 섞이지 않는다. 운영 거울층은 실제 서비스 크기에서 같은 셀을 돌려 측정층 숫자가 현실과 얼마나 다른지 보여 준다. 거울층에서는 `CPUCreditBalance`와 `CPUSurplusCreditBalance`를 함께 기록한다. 거울층 RDS는 unlimited 모드라 크레딧이 바닥나도 CPU가 기준 성능으로 깎이지 않고 초과분 요금만 붙으므로 그 셀도 유효하다. 대신 크레딧이 바닥난 시각과 초과 크레딧 최대값, `CPUSurplusCreditsCharged`를 결과에 적어 다음 거울층 측정(T10)이 같은 상태에서 돌았는지 비교한다.

api 컨테이너는 `SPRING_PROFILES_ACTIVE=local`에 환경변수로 DB, Redis, Kafka 주소를 받는다. Hikari는 기본값(최대 10)을 시리즈 내내 그대로 쓰고, `/actuator/prometheus`는 perf 호스트에서만 환경변수로 노출한다.

인스턴스, 보안 그룹, IAM, 출력 키, 시간당 비용과 보관 비용은 [`../terraform/README.md`](../terraform/README.md)에 있다. 모니터링 스택과 대시보드 패널은 [`../monitoring/README.md`](../monitoring/README.md)에 있다.

## 세션 흐름

노트북에서 `make -C loadtest/search <타깃> 변수=값`으로 부른다. AWS 프로파일은 기본 `aechak`이고 `aws sso login --profile aechak` 세션이 필요하다. 어느 타깃이든 `DRY_RUN=1`을 붙이면 실행하지 않고 AWS 명령만 출력한다.

```bash
cd loadtest/search

# 0. terraform apply 뒤 한 번. 출력을 .perf/outputs.json과 S3 state/outputs.json에 둔다
make outputs

# 1. jar 빌드와 업로드. 마지막 줄에 REV=<sha12> 또는 REV=<sha12>-dirty-<8hex>를 찍는다
make jar REV=search-baseline

# 2. 세션 시작. 이 데이터셋의 RDS만 켜고 다른 perf RDS는 멈춘다. 러너 모니터링 compose까지 띄운다
make up DATASET=100k

# 3. 데이터셋마다 처음 한 번. 첫 부팅(Flyway, ddl-auto=update), exporter 계정, 시드, ANALYZE,
#    manifest(results/manifest/100k.json), warm set, RDS 스냅샷 aechak-perf-100k-baseline
make init DATASET=100k REV=<기준 rev>

# 4. 측정. 변형 하나 또는 before와 after 한 쌍
make variant STEP=SEARCH-T01 DATASET=100k VARIANT=base REV=<rev>
make step STEP=SEARCH-T02 DATASET=100k BEFORE_REV=<rev> AFTER_REV=<rev> AFTER_DDL="SEARCH-T02-a"
make explain STEP=SEARCH-T02 DATASET=100k REV=<rev>
make equiv STEP=SEARCH-T02 DATASET=100k ENGINE=jpa REV=<rev> LABEL=before

# 5. 화면 보기와 결과 받기
make grafana           # 러너 3000을 localhost:13000으로 포워딩
make fetch RUN=2026-11-05-SEARCH-T01-100k
make graphs

# 6. 세션 끝. 태그 Project=aechak-perf인 EC2와 RDS를 모두 멈춘다
make down
```

`make variant`는 jar가 S3에 있는지 확인하고, kit를 묶어 올리고(`make sync`), SSM으로 러너에서 `run.sh`를 돌린다. 끝나면 출력된 `RUN_ID`마다 raw와 캡처를 받아 오고 `tools/summarize.py`로 `results/<STEP>.md`에 표를 붙인다. 종료 코드는 `run.sh`의 종료 코드를 그대로 돌려준다. 러너의 `run.sh`는 run이 끝날 때마다 `RUN_ID` 줄을, 변형이 끝나면 `VARIANT_DONE` 줄을 `s3://<bucket>/progress/<날짜>-<STEP>-<DATASET>-<VARIANT>.log`에 덧붙이고 노트북은 SSM 명령이 끝날 때까지 30초마다 새 줄을 찍는다. local 모드는 이 로그를 쓰지 않는다.

| 다른 타깃 | 하는 일 |
|---|---|
| `make seed DATASET=..`, `make reseed DATASET=..` | seed.sql로 세 테이블을 비우고 같은 값을 다시 넣은 뒤 ANALYZE와 warm set |
| `make manifest DATASET=..` | manifest를 다시 만든다 |
| `make prom` | 러너 Prometheus 9090을 localhost:19090으로 |
| `make shell-runner`, `make shell-api` | SSM 세션 |
| `make reindex DATASET= REV=` | 엔진을 es로 둔 api를 재색인 속성(`AECHAK_SEARCH_ES_REINDEX_ON_STARTUP=true`)과 함께 다시 띄우고 ES 색인 문서 수가 상품 수와 같아질 때까지 기다린다. 걸린 시간과 색인 크기를 `REINDEX_DONE` 줄로 남긴다 |

`make jar`는 `loadtest/` 밖에 커밋하지 않은 변경이 있으면 멈춘다. `ALLOW_DIRTY=1`이면 rev 뒤에 `-dirty-<diff 해시>`를 붙여 빌드한다. HEAD가 아닌 커밋은 `.perf/build/` 아래 임시 워크트리에서 빌드한다. `SKIP_TESTS=1`이면 검색 테스트를 건너뛰고 `search_test_exit=skipped`로 적는다.

`make variant`와 `make step`은 아래 변수를 받는다. SSM은 노트북 환경변수를 넘기지 않아서 make가 이 값을 러너 명령줄에 붙인다.

| 변수 | 기본값 | 하는 일 |
|---|---|---|
| `BASE_RATE` | 없음 | steady와 spike의 기준 요청률. `run.sh --base-rate`로 넘어가고 출처는 manual로 적는다 |
| `FORCE=1` | 꺼짐 | 이미 있는 run id를 지우고 다시 잰다(`run.sh --force`) |
| `MAX_VUS_CAP` | 1500 | k6 VU 상한 |
| `IDLE_RDS_CPU_MAX` | 5 | 변형 시작에서 기다리는 RDS CPU 유휴 기준(%) |
| `RAMP_FLOOR_RATES` | `1 0.5 0.25`, QUICK은 `1 0.5` | ramp 첫 단계가 SLO를 못 지키면 내려가며 시도할 요청률 |
| `RAMP_RATES` | `2 4 8 16 32 64 128 256` | ramp가 올라갈 요청률(QUICK에는 쓰지 않는다). 처리 한계가 256 req/s보다 높으면 더 높은 단계를 준다 |
| `WARMUP_RATE` | 2 | 워밍업 요청률 |

## 셀과 시간

변형(코드나 DDL 상태) 하나마다 데이터 크기 하나에서 아래 순서로 돈다. 부하 수치는 모두 초당 HTTP 요청(req/s)이다. 혼합 반복 하나는 요청 약 1.4개라서 `search.js`가 반복률로 바꾸고, 결과 표에는 실제 `http_reqs` 비율을 적는다. 검색어, 필터, 요청 흐름은 반복 순번에 무리수를 곱한 값의 소수 부분으로 고른다. 같은 셀은 before와 after가 같은 순서로 요청하고 반복이 24회뿐인 run도 검색어 비율이 가중치에 가깝다. 다음 페이지는 앞 응답이 성공했을 때만 보내므로 실제 요청 수는 응답에 따라 달라진다. steady는 반복마다 순번에 0, 100000, 200000을 더해서 세 run이 서로 다른 순서를 쓰고 before와 after는 같은 반복끼리 순서가 같다. 다른 셀은 순번 0부터 시작한다. 짧은 run은 검색어 비율이 맞아도 검색어와 흐름의 조합까지 고르게 들지는 않는다(24회면 강아지 두세 번이 모두 첫 페이지일 수 있다). 그래서 짧은 셀의 차이는 결과 표의 실제 요청 구성과 함께 본다.

| 셀 | 내용 | 벽시계 |
|---|---|---|
| warmup | api 재시작 직후 2 req/s로 60초. raw는 남기지만 표와 캡처에서 뺀다 | 1분 |
| smoke | 1 VU 30초. 응답과 오류 코드 확인 | 1분 |
| ramp | 2, 4, 8, 16, 32, 64, 128, 256 req/s를 45초씩 올라간다. 첫 페이지 p95 500ms 미만, 실패율 1% 미만, dropped_iterations 0을 지키고 k6가 안전 중단하지 않은 단계는 SLO를 통과한다. p95는 보지 않고 실패율 1% 미만, dropped_iterations 0, k6 종료 코드 0인 단계는 처리 기준을 통과한다. 처리 기준을 지키는 동안 계속 올라가고 처리 기준을 처음 못 지킨 단계에서 멈춘다. 처음 SLO에 실패하기 전까지 SLO를 통과한 마지막 단계가 최대 안정 요청률이고 그 뒤 단계는 이 값을 올리지 않는다. 처리 기준을 통과한 가장 높은 단계가 처리 가능 요청률(`capacity_rate`)이다. 2 req/s부터 SLO를 못 지키면 처리 기준 통과 여부와 무관하게 올라가는 단계가 끝난 뒤 `RAMP_FLOOR_RATES`(1, 0.5, 0.25)를 내려가며 단계마다 유휴 대기를 한 뒤 돌고 SLO를 처음 통과한 단계에서 멈춘다. 이때는 그 단계가 최대 안정 요청률이고 모두 실패하면 0이다. 바닥 단계는 반복이 20회 이상 들도록 길이를 늘린다(0.5 req/s면 58초, 0.25 req/s면 110초). 결과의 단계 표는 단계마다 통과한 기준(SLO, 처리 기준만, 없음)을 적는다 | 최대 6분, 바닥 단계를 돌면 5분 안팎 더 |
| steady | 기준 요청률의 70%로 3분씩 3회. 세 p95의 중앙값과 최소 최대가 결과이고 그 폭이 이 변형의 잡음 폭 | 약 12분 |
| spike | 기준 요청률의 3배를 30초, 이어서 기준 요청률의 절반(최대 2 req/s)으로 탐침 90초. 오류율, 오류 코드, 회복 시간(탐침의 회복 창 이동 p95가 회복 기준 아래로 돌아오기까지), dropped_iterations. 회복 기준은 500ms와 기준 steady 첫 페이지 p95 중앙값의 1.2배 중 큰 쪽이다. 기준 steady는 같은 단계 before의 steady이고 before가 없으면 그 변형 자신의 steady다. steady 행이 없으면 500ms를 쓴다. before와 after는 같은 회복 기준으로 비교하고 spike 표에 회복 기준을 적는다. 회복 창은 5초와 3 / 탐침 요청률(초) 중 긴 쪽이라 0.25 req/s 탐침이면 12초이고 탐침 시간 안에 내려오지 않으면 90초 안에 회복 안 됨처럼 관찰 한도를 적는다. burst는 시작할 때 VU를 `MAX_VUS`만큼 만들어 둔다 | 2분 |
| sequential | 1 VU로 검색어 x 정렬 x 페이지(1, 2)마다 반복. 10만 건은 20회, 100만 건과 거울층은 등급 대표 검색어만 3회 | 약 3분 |
| explain | 유휴 상태에서 고정 요청의 목록과 COUNT 쿼리에 EXPLAIN ANALYZE 두 번, 두 번째를 남긴다 | 2분 |

변형 하나는 25분에서 30분이고, before와 after 한 단계는 10만 건만이면 약 1시간, 100만 건까지면 약 2시간이다. `CELLS="smoke ramp"`처럼 일부만 돌릴 수 있다.

k6는 요청률을 분당 반복 수로 바꿔 보내므로 0.25 req/s 같은 소수 요청률도 목표와 5% 안으로 맞는다. 실제로 보낼 요청률은 요약 JSON의 `kit.effective_req_rate`에 남는다. VU 상한(`MAX_VUS`)은 초당 반복 수(요청률 / 1.4) x 반복 하나가 차례로 보낼 수 있는 최대 요청 수 3(첫 페이지와 다음 페이지 둘) x 요청 타임아웃 x 1.2이고 최소 20이며 `MAX_VUS_CAP`에서 자른다. 10만 건에서 6 req/s면 155이고 기본 상한 1500은 약 58 req/s부터 걸린다. 요청 타임아웃은 10만 건과 거울층이 10초, 100만 건이 30초다. 100만 건은 기준선 첫 페이지 하나가 유휴 상태에서도 6초에서 9.5초라 10초로는 부하가 거의 없어도 실패가 나기 때문이고 2026-10-15 기준 측정과 같은 값이다. run의 env.txt `request_timeout`에 적는다. rate 시나리오와 burst는 단계가 끝날 때 남은 반복이 요청을 타임아웃까지 기다릴 수 있게 `gracefulStop`을 반복당 최대 요청 수 x 요청 타임아웃(최소 30초)으로 둔다. rate 시나리오(warmup, ramp 단계, steady)는 실패율이 50% 이상이면 15초 뒤 k6가 멈추고 종료 코드 99를 낸다. spike, smoke, sequential은 멈추지 않는다.

### 기준 요청률

steady와 spike는 기준 요청률로 돈다. `--base-rate`(Makefile `BASE_RATE`)가 있으면 그 값이다. 없으면 이 변형 ramp의 최대 안정 요청률이고 그것이 0이면 처리 가능 요청률이다. 처리 가능 요청률도 0이면 기준 요청률은 0이고 steady와 spike를 돌리지 않고 건너뛴 이유만 남긴다.

처리 가능 요청률을 기준으로 쓰는 경우는 SLO를 지키는 단계가 하나도 없는 느린 변형이다. ramp는 첫 단계에서 SLO에 실패해도 처리 기준을 지키는 동안 계속 올라가므로 이 값은 과부하가 시작되기 전 마지막 단계이고 첫 단계부터 처리 기준을 못 지킬 때만 바닥 단계에서 나온다. 이때 대표 지표인 최대 안정 요청률은 0으로 남고 steady와 spike는 처리 가능 요청률을 비교 부하로 삼아 돈다. 결과 표는 이런 행에 SLO 미충족과 비교 부하를 함께 적는다. 비교 부하에서 잰 지연은 같은 부하에서 before와 after를 견주는 데만 쓰고 SLO를 지켰다는 뜻으로 읽지 않는다.

`make step`은 before를 먼저 돌리고 before ramp env.txt의 `base_rate`를 출처와 상관없이 after의 `--base-rate`로 넘겨서 두 변형이 같은 부하에서 돈다. before가 처리 가능 요청률로 돌았으면 after도 그 비교 부하로 돌고 결과 표 머리에 그렇게 적는다. before의 기준 요청률이 0이면 after는 자기 ramp 결과로 돌고 결과 표 머리에 같은 부하 비교를 할 수 없다고 적는다. env.txt의 `max_stable_rate`와 `capacity_rate`는 언제나 그 변형 자기 ramp의 결과이고 기준 요청률은 `base_rate`와 `base_rate_source`에 따로 적는다. 출처는 own(자기 최대 안정 요청률), capacity(자기 처리 가능 요청률), before(before가 실제로 쓴 기준 요청률), manual(직접 지정) 넷이다.

### 유휴 대기

run마다 시작 전에 유휴 대기를 한다. 2초마다 확인해 아래 조건이 10초 연속 맞으면 20초 더 쉬고 시작한다. 10분 안에 맞지 않으면 종료 코드 7이다.

- processlist에 자기와 `exporter`, `rdsadmin`, `event_scheduler`, `system user` 외에 Sleep이 아닌 행이 없다
- Hikari 대기가 0이다
- ES가 있으면 검색 스레드 풀이 비어 있다

RDS CPU는 변형마다 한 번 api 재시작, DDL, warm set이 끝난 뒤에 본다. CloudWatch 최신 1분 평균이 `IDLE_RDS_CPU_MAX`(기본 5%) 아래가 될 때까지 30초마다 보고 값이 없으면 계속 본다. 10분 안에 내려오지 않으면 종료 코드 7이다. 부하 시나리오는 읽기만 해서 run 뒤에 InnoDB 백그라운드 작업이 남지 않으므로 run 사이에는 RDS CPU를 보지 않는다.

이어서 `events_statements_summary_by_digest`를 비우고 버퍼 풀 읽기 카운터를 적은 뒤 k6를 돌린다.

### 변형 끝

run이 끝날 때 받는 `.cw.csv`와 `.slow.log`는 임시본이다. CloudWatch 지표와 로그는 1분에서 3분 늦게 들어오기 때문이다. 변형의 마지막 run이 끝나면 그 분의 RDS CPU 1분 지표가 들어올 때까지 30초마다 기다리고(최대 6분) 모든 run의 `.cw.csv`와 `.slow.log`를 env.txt의 `start_ms`와 `end_ms`로 다시 받아 S3에 올린다. `.cw.csv`는 run 앞 1분과 뒤 2분까지 받는다. 끝난 분의 지표가 없는 run은 env.txt에 `cw_complete=false`를 적는다. `run.sh`가 중간에 멈춰도 끝난 run이 하나라도 있으면 이 처리와 아래 캡처를 한 번 하고 원래 종료 코드로 끝난다.

Grafana 캡처도 이 뒤에 한꺼번에 찍는다. ramp, steady, spike는 전체 패널과 `00-dashboard.png`를 찍는다. smoke와 sequential은 k6 지연, k6 처리량, k6 오류, RDS CPU, api CPU 다섯 패널만 찍고 warmup과 explain은 찍지 않는다. `capture.sh`는 패널을 3개씩 동시에 렌더한다(`CAPTURE_PARALLEL`). 노트북에서 전체 패널 run 하나를 차례로 렌더하면 약 120초가 걸렸고 3개씩 렌더하면 약 52초가 걸렸다.

## 독립성 규칙

부하 테스트 하나가 다음 테스트에 남기는 것은 데이터 행, 인덱스, 버퍼 풀과 JVM warm 상태, 통계, 로그, 크레딧이다. 각각 이렇게 끊는다.

- 데이터 크기마다 RDS 인스턴스를 따로 둔다. 한 세션에는 한 크기만 재고 다른 인스턴스는 멈춰 둔다(`make up`이 처리한다).
- `seed.sql`은 결정적이다. `NOW()`와 `RAND()`를 쓰지 않고 할인 기간은 2026-10-01 00:00:00부터 2027-06-30 23:59:59까지 절대 날짜다. 검색어 일치 비율은 아래와 같다.

  | 등급 | 검색어 | 전체 상품 중 일치 |
  |---|---|---|
  | high | 강아지 | 50% (노출 상품 기준 약 47%) |
  | mid | 사료, 연어, 간식 | 6.7%에서 10% |
  | low | 관절, 하네스, 바잇미 | 2.5%에서 3.3% |
  | rare | 한정판 | 0.1% |
  | none | 없는상품 | 0% |

- 경계 집합은 `n % 1000`이 503, 504, 505인 상품(0.3%)이다. `description`이 `perf-boundary:expired`, `perf-boundary:upcoming`, `perf-boundary:open`으로 시작하고 각각 이미 끝난 할인, 시작 전 할인, 기간 제한 없는 할인이다. 이름은 바꾸지 않아 검색어 비율이 그대로다. 할인 만료 같은 시간 경계를 보는 셀만 이 행을 쓴다.
- manifest(`results/manifest/<size>.json`)는 테이블별 행 수, 시드 컬럼의 CRC, 활성 할인 수, 노출 상품 수, 검색어별 일치 수, 인덱스 목록, 테이블 크기를 담는다. `run.sh`는 변형마다 행 수와 CRC를 manifest와 비교하고 다르면 종료 코드 3으로 멈춘다. 이때는 `make reseed`로 되돌린다.
- 부하 시나리오는 모두 비로그인 검색이라 DB에 쓰지 않는다. 쓰기가 있는 셀은 세션 맨 끝에 돌리고 끝나면 `make reseed`로 manifest 상태로 되돌린다. 스냅샷 복원은 새 인스턴스를 만들므로 거울층과 사고 복구에만 쓴다.
- 첫 부팅 뒤 api는 항상 `SPRING_FLYWAY_ENABLED=false`와 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`으로 뜬다. 새 jar가 부팅하면서 인덱스나 컬럼을 만들면 before 셀이 두 변수를 함께 바꾼 셀이 되기 때문이다. 스키마 변경은 `ddl/<id>.sql`로만 하고(`ddl/README.md`), `run.sh --ddl "id ..."`가 러너에서 적용해 `perf_applied_ddl`에 id와 파일 sha256을 적는다. 적용된 id가 `--ddl`과 다르거나 파일이 바뀌었으면 종료 코드 4로 멈춘다.
- before 상태로 돌아갈 때는 `make shell-runner`로 러너에 들어가 `results/rollback/<id>.sql`을 손으로 적용한다. 이 파일의 마지막 문장은 `perf_applied_ddl`에서 그 id 행을 지운다.
- 변형마다 api 컨테이너를 다시 만들고 워밍업을 버려 두 jar가 같은 cold start에서 시작한다. 변형마다 `sql/warm.sql`(측정 대상 테이블과 모든 인덱스를 읽는 고정 집합)을 돌린다.
- ES 색인은 전체 재색인으로만 채우고 문서 수가 manifest의 노출 상품 수와 같을 때만 읽기 셀을 돌린다(T07부터).

## 종료 코드

| 코드 | 뜻 |
|---|---|
| 0 | 정상 |
| 1 | 사용법 오류, `FORCE` 없이 이미 있는 run id(AWS), 기준 요청률을 모름(ramp도 `--base-rate`도 없음), history_long consumer를 끄지 못함 |
| 3 | manifest 불일치 |
| 4 | DDL 상태 불일치 |
| 5 | api가 준비되지 않음 |
| 6 | k6 오류. rate 시나리오의 실패율 안전 중단인 99는 env.txt `k6_exit`에 적고 계속한다 |
| 7 | 유휴 대기 10분 초과 |

## 파일

| 경로 | 내용 |
|---|---|
| `Makefile`, `lib/laptop.sh` | 노트북 진입점 |
| `run.sh` | 러너에서 변형 하나의 셀을 순서대로 돈다 |
| `lib/common.sh`, `lib/remote.sh` | 공용 함수, 러너의 시드와 manifest와 equiv |
| `explain.sh`, `capture.sh` | EXPLAIN ANALYZE 집합, Grafana 패널 PNG |
| `search.js`, `equiv.js` | k6 부하 시나리오, 결과 동치 확인 |
| `tools/summarize.py`, `tools/graphs.py`, `tools/equiv_diff.py` | 결과 표, SVG 그래프, equiv 비교 |
| `apihost/` | api 호스트 compose와 `restart.sh` |
| `seed.sql`, `sql/` | 시드, manifest, exporter 계정, warm set |
| `ddl/` | 측정용 스키마 변경 |
| `compose.yml` | 노트북 kit 개발 스택 |
| `es/` | ES 호스트 스택(`compose.yml`, Elasticsearch 9.2.6 단일 노드와 수집기, `make up`이 띄운다)과 T08 nori 시연 이미지 |

run id는 `<YYYY-MM-DD>-<STEP>-<DATASET>-<CELL>-<VARIANT>-r<N>`이고 날짜는 서울 시각이다(예: `2026-11-05-SEARCH-T01-100k-steady-base-r2`). steady만 r1에서 r3까지 있고 ramp는 run id 하나에 단계별 파일을 둔다. AWS에서 같은 run id가 이미 있으면 종료 코드 1로 멈춘다. `FORCE=1`이면 그 run id의 러너 raw 파일, 캡처, S3 객체와 노트북에 받아 둔 파일을 지우고 지운 목록을 로그에 남긴 뒤 다시 잰다. `make step`도 `FORCE`를 따른다. local 모드는 늘 지우고 다시 쓴다.

| 결과 경로 | git | 내용 |
|---|---|---|
| `results/raw/<run-id>.*` | 제외 | `.json`(k6 요약), `.k6.txt`, `.cw.csv`(RDS 지표 1분 단위), `.digest.txt`(다이제스트 상위 10), `.slow.log`, `.env.txt`, ramp의 `.ramp.tsv`(단계마다 `start_ms`, `end_ms`, `k6_exit`)와 `.s<RATE>.json`, spike의 `.points.csv.gz`, explain의 `.explain.txt`, `.prom.json`(run 구간의 Prometheus 값. Hikari 대기와 활성, `Threads_running`은 5초 수집값의 최대다. Hikari 타임아웃과 api CPU 스로틀링 초는 increase 추정이라 소수가 나온다. api CPU 평균과 최대는 30초 rate를 5초마다 본 값이고 단위는 코어다. 서버 p95 최대는 30초 창 히스토그램 추정의 최대이고 30은 가장 큰 버킷이다. 러너 CPU 최대는 코어 평균 사용률이고 러너 가용 메모리 최소는 MiB다. 30초 창은 run 시작 전 30초까지 걸칠 수 있다) |
| `results/captures/<run-id>/` | 골라서 커밋 | 패널 PNG `NN-slug.png`와 `00-dashboard.png` |
| `results/<STEP>.md` | 커밋 | summarize.py가 붙인 표 |
| `results/manifest/<size>.json` | 커밋 | 데이터셋 기준 상태 |
| `results/equiv/<step>-<size>-<engine>[-<label>].jsonl` | 커밋 | equiv.js 결과. 같은 engine으로 before와 after를 비교할 때는 `LABEL`로 이름을 나눈다 |
| `results/rollback/<id>.sql` | 커밋 | DDL 되돌리기 |
| `results/graphs/` | 커밋 | `make graphs` 결과 |
| `jars/`, `.perf/` | 제외 | jar와 메타, outputs.json, 로그, 노트북 결과 |

env.txt에는 run마다 rev, 트리 해시, merge-base, dirty 여부, jar sha256, hibernate-core와 elasticsearch-java 버전, 검색 테스트 종료 코드, RDS id와 클래스와 엔진 버전, 버퍼 풀 크기, 다이제스트 길이와 `long_query_time` 파라미터, api와 러너 인스턴스 타입, api 상한, Hikari 최대값, 적용 DDL, 스키마와 인덱스 해시, manifest 일치, 변형 환경변수, 요청률, 시나리오, `max_vus`, k6 버전, 버퍼 풀 디스크 읽기 차이, dropped_iterations, 최대 안정 요청률이 들어간다. 이어서 k6 종료 코드(`k6_exit`, ramp는 최대 안정 단계의 값), k6 최대 RSS(`k6_max_rss_kb`, 러너에 GNU time이 있을 때만), 기준 요청률과 출처(`base_rate`, `base_rate_source`), CloudWatch 다시 받기 결과(`cw_complete`), 건너뛴 셀의 이유(`skip_reason`), spike 탐침 요청률(`probe_rate`), ramp의 처리 가능 요청률(`capacity_rate`), k6 요청 타임아웃(`request_timeout`), kit 소스 해시(`kit_hash`, search의 sh, js, py, sql, yml, Makefile과 monitoring의 yml을 경로 순서로 이어 붙인 sha256 앞 12자리. `make sync`가 같은 묶음을 `s3://<bucket>/kit/<kit_hash>.tgz`로도 올린다), 변형을 시작할 때의 history_long consumer 상태(`ps_history_long`), steady 반복의 순번 오프셋(`trace_offset`)이 들어간다. 모르는 값은 `na`다.

## 지표와 판정

run마다 항상 기록하는 것은 k6의 p50, p95, p99, 최대값, 처리량, 실패율, 응답 코드와 오류 코드 분포다. 여기에 RDS CPU와 접속 수, `Threads_running`, 버퍼 풀 디스크 읽기, api 컨테이너 CPU와 메모리, JVM 힙, Hikari 활성과 대기와 타임아웃, HTTP 서버 p95, 쿼리별 다이제스트를 더하고 T07부터는 ES CPU, 힙, 검색 지연도 남긴다. 문제가 생겼을 때만 보는 것이 아니라 매번 같은 표와 같은 캡처로 남긴다.

대표 지표는 첫 페이지 p95 500ms 미만과 실패율 1% 미만을 지키는 최대 안정 요청률이다. ramp에서 찾고 steady에서 확인한다. 단계 사이 값은 재지 않으므로 결과 표의 ramp 행은 최대 안정 단계와 그보다 높은 단계 중 처음 실패한 단계를 함께 적는다(예: 8 (16에서 실패)). 실패한 단계 없이 끝났으면 실패 단계 없음이라고 적는다. 최대 안정 요청률이 0이면 SLO 미충족과 처리 가능 요청률을 적는다(예: SLO 미충족 (처리 가능 0.5)).

결과 표의 RDS CPU 평균은 run 구간 안에 온전히 든 CloudWatch 1분 지표만 평균한 값이다. 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수이고 vCPU는 env.txt의 인스턴스 클래스로 정한다(`db.m7g.large`와 `db.t4g.micro`는 2, 모르는 클래스는 비운다). 온전히 든 1분이 없으면 겹친 시간으로 가중한 평균을 쓰고 값 앞에 ≈를 붙인다. 요청당 RDS CPU초는 run 전체의 요청률로 나누므로 도착률이 일정하고 dropped_iterations가 0이며 실패율이 1% 미만인 steady 행에서만 ≈ 없이 적고 아래 채택 조건 3번은 steady 행의 값만 비교한다. ramp 대표 행의 지연, 처리량, CPU, dropped는 모두 최대 안정 단계 구간의 값이고(SLO를 통과한 단계가 없으면 마지막 단계의 dropped를 적고 셀 이름에 표시한다) 단계 표의 CPU도 같은 규칙을 따른다. k6 종료 코드가 0이 아닌 행, `cw_complete=false`인 행, dropped_iterations가 있는 spike 행(부하 미달)은 표에 따로 표시한다.

한 단계의 변경은 아래를 모두 만족할 때만 채택한다. 하나라도 어긋나면 측정했지만 채택하지 않았다고 기록한다.

1. steady p95의 개선이 잡음 폭 밖이다. before와 after의 세 회 범위가 겹치지 않고 중앙값 차이가 폭보다 크다. steady 첫 페이지가 run당 수십 건뿐인 100만 건은 몇 % 차이를 steady p95로 판정하지 않고 sequential 중앙값과 steady p50을 함께 본다.
2. 꼬리 지연이 나빠지지 않는다. steady p99는 run당 첫 페이지가 150건 안팎이면 가장 느린 두세 건이 정하므로 기록만 한다. p99가 before보다 잡음 폭 넘게 나빠 보이면 같은 조건에서 첫 페이지 1,000건 이상을 담은 확인 셀을 따로 돌아 판정한다. 결과 표는 p99 옆에 그 백분위를 계산한 표본 수 n을 적고 n이 100 미만이면 p99 대신 표본 부족(n=40처럼)을 적는다.
3. 같은 부하에서 요청당 RDS CPU초가 before 세 run의 최댓값을 넘지 않는다. 1분 지표에서 나온 추정치라 before의 범위를 허용 폭으로 쓴다.
4. 포화 아래에서 Hikari 대기가 0이고 실패율이 1% 미만이다.
5. spike 회복 시간이 나빠지지 않는다.
6. equiv diff가 비어 있다. `tools/equiv_diff.py`가 혼합 검색어에서 차이를 찾으면 종료 코드 1이다.

다음 단계를 고르는 병목 판정은 포화점에서 한다. RDS CPU가 80% 이상이면 DB CPU 병목, api CPU가 80% 이상이면 api 병목(이때는 api를 2 vCPU로 한 번 더 재서 둘 다 적는다), Hikari 대기가 쌓이는데 RDS CPU가 낮으면 락이나 긴 트랜잭션 같은 커넥션 점유, 버퍼 풀 디스크 읽기가 0이 아니면 메모리 병목이다. sequential은 검색어마다 표본이 작아서 중앙값, p90, 최대값만 적는다.

## 노트북 local 모드

노트북 모드는 kit 자체를 고치고 확인할 때만 쓴다. 노트북 Docker의 수치는 어디에도 기록하지 않는다. 결과는 `.perf/local/` 아래로만 가고 `results/`에는 쓰지 않는다. 포트는 mysql 13306, api 18080과 18081, Grafana 13000, Prometheus 19090이다.

```bash
cd loadtest/search
make jar LOCAL=1 ALLOW_DIRTY=1 SKIP_TESTS=1        # REV=... 출력
# 모니터링 스택. perf-mysqlcli 컨테이너가 kit의 SQL 클라이언트다
../monitoring/targets.sh --local
docker compose -f ../monitoring/compose.yml --env-file ../monitoring/.local/compose.env up -d
# 첫 부팅, exporter 계정, 시드, manifest. LOCAL_N_PRODUCTS로 건수를 줄일 수 있다
make init LOCAL=1 DATASET=local REV=<rev>
LOCAL_N_PRODUCTS=10000 make seed LOCAL=1 DATASET=local && make manifest LOCAL=1 DATASET=local
# 모든 셀을 짧게. QUICK=1은 ramp 2 4 8(바닥 단계 1 0.5)을 10초씩, steady 30초, spike 10초와 탐침 20초
make variant LOCAL=1 QUICK=1 STEP=SEARCH-T00 DATASET=local VARIANT=smoke REV=<rev>
# 정리
docker compose -p aechak-perf -f compose.yml down -v
docker compose -f ../monitoring/compose.yml --env-file ../monitoring/.local/compose.env down -v
```

api만 다시 띄울 때는 `PERF_ENV=local apihost/restart.sh --rev <rev> --dataset local [--first-boot]`이고 jar는 `jars/api-<rev>.jar`를 쓴다. 노트북 mysql의 slow log는 컨테이너 안 `/var/lib/mysql/slow.log`다. 노트북에는 CloudWatch가 없어서 `.cw.csv`는 헤더만 있고 RDS와 로그 패널(1, 2, 17에서 20)은 비어 있다.
