#!/usr/bin/env bash
# 측정 kit 공용 함수. run.sh, explain.sh, lib/remote.sh, lib/laptop.sh가 source한다
# 노트북(macOS bash 3.2)과 러너(AL2023 bash 5) 양쪽에서 돌아야 해서 bash 4 기능을 쓰지 않는다
# shellcheck disable=SC2034,SC2119,SC2120  # 여기서 정한 값은 source한 스크립트가 쓴다
set -euo pipefail

KIT=${KIT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}
KIT_VERSION=3
# k6 v2.2.0의 Prometheus remote write 출력 이름. 이름이 바뀌면 여기만 고친다
K6_PROM_OUT=experimental-prometheus-rw
K6_BIN=${K6_BIN:-k6}
PERF_ROOT=${PERF_ROOT:-/opt/perf}
MYSQLCLI=${MYSQLCLI:-perf-mysqlcli}
DRY_RUN=${DRY_RUN:-0}
# SSM RunShellScript는 /etc/profile.d를 읽지 않아 aws CLI 리전을 여기서 정한다
export AWS_REGION=${AWS_REGION:-ap-northeast-2}
export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-$AWS_REGION}
# 러너(UTC)와 노트북의 로그 시각을 KST로 맞춘다. epoch ms와 CloudWatch 시각은 시간대와 무관하다
export TZ=Asia/Seoul
E_MANIFEST=3 E_DDL=4 E_API=5 E_K6=6 E_IDLE=7

log()  { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
warn() { log "WARN $*"; }
die()  { local c=$1; shift; log "ERROR $*"; exit "$c"; }
is_dry() { [ "$DRY_RUN" = 1 ]; }

# DRY_RUN=1이면 실행하지 않고 명령만 찍는다
x() {
  if is_dry; then printf 'DRY:' >&2; printf ' %q' "$@" >&2; printf '\n' >&2; return 0; fi
  "$@"
}

now_ms() { python3 -I -c 'import time; print(int(time.time()*1000))'; }
iso_of_ms() { python3 -I -c 'import sys,datetime; print(datetime.datetime.fromtimestamp(int(sys.argv[1])/1000, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))' "$1"; }
sha256() { if command -v sha256sum >/dev/null; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi; }
sha256_file() { sha256 < "$1"; }
nth() { echo "$1" | awk -v n="$2" '{print $n}'; }
# 초당 혼합 반복(요청률 / 1.4) x 반복 하나의 최대 요청 3건 x 요청 타임아웃(REQ_TIMEOUT_S, 기본 10초) x 1.2를 MAX_VUS_CAP(기본 1500)에서 자른다
# 1.4와 3은 search.js의 REQS_PER_MIXED_ITER와 MAX_REQS_PER_MIXED_ITER다. 잘리면 로그를 남긴다
max_vus() {
  local cap=${MAX_VUS_CAP:-1500} v
  v=$(awk -v r="$1" -v t="${REQ_TIMEOUT_S:-10}" 'BEGIN{v=r/1.4*3*t*1.2; c=int(v); if (c<v) c++; if (c<20) c=20; print c}')
  if [ "$v" -gt "$cap" ]; then log "MAX_VUS ${v}를 MAX_VUS_CAP ${cap}으로 줄인다 (요청률 $1)"; v=$cap; fi
  echo "$v"
}
mul() { awk -v a="$1" -v b="$2" 'BEGIN{printf "%g\n", a*b}'; }

# SQL은 stdin으로 받고 인자는 mysql 옵션으로 덧붙인다(-r 등)
sql() {
  if is_dry; then local q; q=$(cat); printf 'DRY: sql %s\n' "$(printf '%s' "$q" | LC_ALL=C tr '\n' ' ' | LC_ALL=C cut -c1-160)" >&2; return 0; fi
  docker exec -i -e MYSQL_PWD="$DB_PASS" "$MYSQLCLI" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
    --default-character-set=utf8mb4 -N -B "$@" aechak
}

# 데이터셋으로 DB, URL, 출력 경로를 정한다. local이면 노트북 compose 기본값을 쓴다
resolve_env() {
  DATASET=$1 ES_URL='' RDS_CLASS=na API_ID='' API_IP='' RUNNER_ID=''
  if [ "$1" = local ]; then
    LOCAL=1 BUCKET='' RDS_ID=local OUT=$KIT/.perf/local
    DB_HOST=${LOCAL_DB_HOST:-host.docker.internal} DB_PORT=${LOCAL_DB_PORT:-13306}
    DB_USER=${LOCAL_DB_USER:-root} DB_PASS=${LOCAL_DB_PASS:-local}
    BASE_URL=http://localhost:18080/api/v1 PROM_URL=http://localhost:19090 GRAFANA_URL=http://localhost:13000
    export DB_HOST DB_PORT DB_USER DB_PASS BASE_URL
    return 0
  fi
  LOCAL=0 OUT=$PERF_ROOT/results
  OUTPUTS=${OUTPUTS:-$PERF_ROOT/state/outputs.json}
  [ -s "$OUTPUTS" ] || die 1 "outputs.json 없음: $OUTPUTS"
  local db
  if [ "$1" = mirror ]; then db=$(jq -c '.mirror.value' "$OUTPUTS"); else db=$(jq -c --arg d "$1" '.rds.value[$d]' "$OUTPUTS"); fi
  [ "$db" != null ] || die 1 "outputs에 $1 DB가 없다"
  RDS_ID=$(jq -r .id <<<"$db") RDS_CLASS=$(jq -r .instance_class <<<"$db")
  DB_HOST=$(jq -r .address <<<"$db") DB_PORT=$(jq -r .port <<<"$db")
  DB_USER=$(jq -r .db_username.value "$OUTPUTS")
  BUCKET=$(jq -r .bucket.value "$OUTPUTS")
  API_ID=$(jq -r .api.value.instance_id "$OUTPUTS") API_IP=$(jq -r .api.value.private_ip "$OUTPUTS")
  RUNNER_ID=$(jq -r .runner.value.instance_id "$OUTPUTS")
  local es; es=$(jq -r '.es.value.private_ip // empty' "$OUTPUTS")
  if [ -n "$es" ]; then ES_URL=http://$es:9200; fi
  BASE_URL=http://$API_IP:8080/api/v1 PROM_URL=http://localhost:9090 GRAFANA_URL=http://localhost:3000
  DB_PASS=$(x aws ssm get-parameter --with-decryption --name "$(jq -r .db_password_param.value "$OUTPUTS")" \
    --query Parameter.Value --output text)
  export DB_HOST DB_PORT DB_USER DB_PASS BASE_URL
}

ssm_param() { x aws ssm get-parameter --with-decryption --name "$(jq -r ".$1.value" "$OUTPUTS")" --query Parameter.Value --output text; }

prom_query() {
  curl -sf --max-time 5 "$PROM_URL/api/v1/query" --data-urlencode "query=$1" | jq -r '.data.result[0].value[1] // empty'
}

# prom_export <start_ms> <end_ms>: run 구간의 Hikari, api CPU와 스로틀링, MySQL 실행 스레드, 서버 p95, 러너 CPU와 메모리, ES 호스트 CPU와 힙을 JSON 한 줄로 찍는다. 없는 값은 null
prom_export() {
  local e=$(( $2 / 1000 )) d=$(( ($2 - $1) / 1000 )) k q v out=
  if [ "$d" -lt 15 ]; then d=15; fi
  while IFS='|' read -r k q; do
    v=$(curl -sf --max-time 10 "$PROM_URL/api/v1/query" --data-urlencode "query=${q//@D/${d}s}" --data-urlencode "time=$e" \
      | jq -r '.data.result[0].value[1] // empty' 2>/dev/null || true)
    out="$out $(jq -nc --arg k "$k" --arg v "$v" '{($k): ($v | tonumber? // null)}')"
  done <<'EOF'
hikari_pending_max|max_over_time(sum(hikaricp_connections_pending{job="spring"})[@D:5s])
hikari_active_max|max_over_time(sum(hikaricp_connections_active{job="spring"})[@D:5s])
hikari_timeouts|sum(increase(hikaricp_connections_timeout_total{job="spring"}[@D]))
api_cpu_avg|avg_over_time(sum(rate(container_cpu_usage_seconds_total{job="cadvisor",container_label_com_docker_compose_service="api"}[30s]))[@D:5s])
api_cpu_max|max_over_time(sum(rate(container_cpu_usage_seconds_total{job="cadvisor",container_label_com_docker_compose_service="api"}[30s]))[@D:5s])
api_cpu_throttled_s|sum(increase(container_cpu_cfs_throttled_seconds_total{job="cadvisor",container_label_com_docker_compose_service="api"}[@D]))
threads_running_max|max_over_time(mysql_global_status_threads_running{job="mysqld"}[@D])
server_p95_max_s|max_over_time(histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="spring",uri="/api/v1/search/products"}[30s])))[@D:5s])
runner_cpu_max|max_over_time((1 - avg(rate(node_cpu_seconds_total{job="node",host="runner",mode="idle"}[30s])))[@D:5s])
runner_mem_available_min_mib|min_over_time(node_memory_MemAvailable_bytes{job="node",host="runner"}[@D]) / 1048576
es_cpu_avg|avg_over_time((1 - avg(rate(node_cpu_seconds_total{job="node",host="es",mode="idle"}[30s])))[@D:5s])
es_cpu_max|max_over_time((1 - avg(rate(node_cpu_seconds_total{job="node",host="es",mode="idle"}[30s])))[@D:5s])
es_heap_max_ratio|max_over_time((sum(elasticsearch_jvm_memory_used_bytes{job="es",area="heap"}) / sum(elasticsearch_jvm_memory_max_bytes{job="es",area="heap"}))[@D:5s])
EOF
  echo "$out" | jq -sc add
}

# kit_hash: kit 소스(search의 sh, js, py, sql, yml, Makefile과 monitoring의 yml)를 경로 순서로 이어 붙인 sha256 앞 12자리
kit_hash() {
  (cd "$KIT/.." && find search monitoring \( -path search/results -o -path search/jars -o -path search/.perf -o -path search/tools/.venv \
    -o -path monitoring/.local \) -prune -o -type f \( -name '*.sh' -o -name '*.js' -o -name '*.py' -o -name '*.sql' -o -name '*.yml' \
    -o -name Makefile \) -print | LC_ALL=C sort | xargs cat) | sha256 | cut -c1-12
}

# ssm_run <instance-id> <command> [timeout-seconds] [진행 로그 s3 경로]: 원격 명령을 보내고 끝날 때까지 기다려 stdout을 찍는다. 반환값은 원격 종료 코드
# 진행 로그 경로를 주면 30초마다 새 줄을 찍는다
ssm_run() {
  local id=$1 cmd=$2 to=${3:-3600} prog=${4:-} cid st rc params t0=$SECONDS last=$SECONDS shown=0 lines n
  if is_dry; then printf 'DRY: ssm %s (timeout %s): %s\n' "$id" "$to" "$cmd" >&2; return 0; fi
  params=$(jq -nc --arg c "$cmd" --arg t "$to" '{commands:[$c], executionTimeout:[$t]}')
  cid=$(aws ssm send-command --instance-ids "$id" --document-name AWS-RunShellScript --parameters "$params" \
    --timeout-seconds 600 --output-s3-bucket-name "$BUCKET" --output-s3-key-prefix ssm-out \
    --query Command.CommandId --output text)
  log "ssm $id command $cid"
  while :; do
    sleep 5
    st=$(aws ssm get-command-invocation --command-id "$cid" --instance-id "$id" --query Status --output text 2>/dev/null || echo Pending)
    case $st in Pending|InProgress|Delayed) ;; *) break ;; esac
    if [ -n "$prog" ]; then
      if [ $((SECONDS - last)) -ge 30 ]; then
        last=$SECONDS
        lines=$(aws s3 cp --only-show-errors "$prog" - 2>/dev/null || true)
        n=$(printf '%s\n' "$lines" | grep -c . || true)
        if [ "$n" -gt "$shown" ]; then printf '%s\n' "$lines" | sed -n "$((shown + 1)),\$p" >&2; shown=$n; fi
      fi
    elif [ $(( (SECONDS - t0) % 300 )) -lt 5 ]; then log "ssm $cid $st $((SECONDS - t0))s"; fi
  done
  # API 응답의 출력은 24000자에서 잘려서 전체 출력은 S3에서 읽는다
  local key="s3://$BUCKET/ssm-out/$cid/$id/awsrunShellScript/0.awsrunShellScript"
  aws s3 cp --only-show-errors "$key/stdout" - 2>/dev/null \
    || aws ssm get-command-invocation --command-id "$cid" --instance-id "$id" --query StandardOutputContent --output text
  aws s3 cp --only-show-errors "$key/stderr" - >&2 2>/dev/null || true
  if [ "$st" = Success ]; then return 0; fi
  rc=$(aws ssm get-command-invocation --command-id "$cid" --instance-id "$id" --query ResponseCode --output text 2>/dev/null || echo 1)
  log "ssm $cid $st rc=$rc"
  if [ "$rc" -gt 0 ] 2>/dev/null; then return "$rc"; fi
  return 1
}

# restart_api [restart.sh 인자...]: api를 재시작하고 API_READY 줄이 나오면 0
restart_api() {
  local out rc=0 a prev='' to=900 cmd="/opt/perf/bin/kit-pull && bash /opt/perf/kit/search/apihost/restart.sh"
  if [ "$LOCAL" = 1 ]; then
    out=$(x env PERF_ENV=local bash "$KIT/apihost/restart.sh" "$@") || rc=$?
  else
    for a in "$@"; do
      cmd="$cmd $(printf '%q' "$a")"
      # 준비 대기를 늘리면 SSM 명령 상한도 그만큼 늘린다
      if [ "$prev" = --ready-timeout ]; then to=$((a + 660)); fi
      prev=$a
    done
    out=$(ssm_run "$API_ID" "$cmd" "$to") || rc=$?
  fi
  printf '%s\n' "$out" >&2
  if is_dry; then return 0; fi
  [ "$rc" = 0 ] && printf '%s\n' "$out" | grep -q '^API_READY '
}

# cw_fetch <db-id> <from-ms> <to-ms> <out.csv> [추가 지표...]: 앞 1분, 뒤 2분을 더한 구간의 RDS 지표를 1분 단위 csv로 받는다
cw_fetch() {
  local id=$1 from to out=$4 m
  echo 'timestamp_iso,metric,stat,value' > "$out"
  if [ "$id" = local ]; then return 0; fi
  from=$(iso_of_ms $(( $2 - 60000 ))); to=$(iso_of_ms $(( $3 + 120000 )))
  shift 4
  for m in CPUUtilization DatabaseConnections ReadIOPS FreeableMemory "$@"; do
    # TZ=Asia/Seoul이면 CLI가 시각을 +09:00으로 바꿔 찍어 변형 끝의 cw_complete 확인(UTC 분)과 맞지 않는다
    x env TZ=UTC aws cloudwatch get-metric-statistics --namespace AWS/RDS --metric-name "$m" \
      --dimensions Name=DBInstanceIdentifier,Value="$id" --start-time "$from" --end-time "$to" \
      --period 60 --statistics Average Maximum --output json \
    | jq -r --arg m "$m" '(.Datapoints // []) | sort_by(.Timestamp)[]
        | "\(.Timestamp),\($m),Average,\(.Average)", "\(.Timestamp),\($m),Maximum,\(.Maximum)"' >> "$out" \
    || warn "cloudwatch $m 실패"
  done
}

# slowlog_fetch <db-id> <from-ms> <to-ms> <out>: CloudWatch Logs에서 run 구간의 slow log만 받는다
slowlog_fetch() {
  x aws logs filter-log-events --log-group-name "/aws/rds/instance/$1/slowquery" \
    --start-time "$2" --end-time "$3" --output json \
  | jq -r '(.events // [])[].message' > "$4" || warn "slow log 받기 실패: $1"
}

# 노트북 compose mysql 컨테이너의 slow log 파일을 바이트 위치로 자른다
local_slow_init() {
  SLOW_CID=$(docker ps -q --filter label=com.docker.compose.project=aechak-perf --filter label=com.docker.compose.service=mysql | head -1)
  SLOW_PATH=$(echo "SELECT IF(LEFT(@@slow_query_log_file,1)='/', @@slow_query_log_file, CONCAT(@@datadir, @@slow_query_log_file))" | sql)
}
local_slow_size() {
  if is_dry || [ -z "${SLOW_CID:-}" ]; then echo 0; return 0; fi
  docker exec "$SLOW_CID" sh -c 'wc -c < "$1" 2>/dev/null || echo 0' _ "$SLOW_PATH" | tr -d ' '
}
local_slow_copy() {  # $1 시작 바이트 $2 출력 파일
  if is_dry || [ -z "${SLOW_CID:-}" ]; then : > "$2"; return 0; fi
  docker exec "$SLOW_CID" tail -c +"$(( $1 + 1 ))" "$SLOW_PATH" > "$2" || warn "local slow log 복사 실패"
}

# upload_run <run-id>: raw 파일과 캡처를 s3://<bucket>/results/<run-id>/{raw,captures}/로 올린다
upload_run() {
  if [ -z "${BUCKET:-}" ]; then return 0; fi
  x aws s3 cp --recursive --only-show-errors "$OUT/raw/" "s3://$BUCKET/results/$1/raw/" --exclude '*' --include "$1.*"
  if [ -d "$OUT/captures/$1" ]; then
    x aws s3 cp --recursive --only-show-errors "$OUT/captures/$1/" "s3://$BUCKET/results/$1/captures/"
  fi
}

# k6_eval <summary.json>: "http_reqs/s  첫페이지 p95  p99  실패%  dropped  pass" 한 줄(탭 구분)
# pass는 첫 페이지 p95 500ms 미만, 실패율 1% 미만, dropped_iterations 0일 때 1
k6_eval() {
  if [ ! -s "$1" ]; then printf 'na\tna\tna\tna\tna\t0\n'; return 0; fi
  jq -r '
    def v(m; s): .metrics[m].values[s];
    def r2: if . == null then "na" else (. * 100 | floor) / 100 end;
    (v("http_req_duration{kind:first}"; "p(95)")) as $p95
    | ((v("http_req_failed"; "rate") // 0) * 100) as $f
    | (v("dropped_iterations"; "count") // 0) as $d
    | [ (v("http_reqs"; "rate") | r2), ($p95 | r2), (v("http_req_duration{kind:first}"; "p(99)") | r2),
        ($f | r2), $d, (if $p95 != null and $p95 < 500 and $f < 1 and $d == 0 then 1 else 0 end) ]
    | map(tostring) | join("\t")' "$1"
}

ENV_KEYS="run_id step dataset cell variant rep start_ms end_ms date_kst kit_version git_rev git_tree merge_base patch_id dirty jar jar_sha256 hibernate_core elasticsearch_java search_test_exit rds_id rds_class rds_engine_version buffer_pool_size_bytes param_max_digest_length param_perf_schema_max_digest_length param_long_query_time api_instance_type api_cpus api_mem runner_instance_type hikari_max applied_ddl schema_hash index_hash manifest_match search_env req_rate scenario max_vus k6_version bp_reads_delta bp_read_requests_delta bp_disk_read_flag dropped_iterations max_stable_rate k6_exit k6_max_rss_kb base_rate base_rate_source cw_complete skip_reason probe_rate capacity_rate request_timeout kit_hash ps_history_long trace_offset"

# write_env <file>: ENV_<key> 변수를 key=value로 쓴다. 비었으면 na
write_env() {
  local k v
  for k in $ENV_KEYS; do
    v=ENV_$k; v=${!v:-na}
    printf '%s=%s\n' "$k" "$(printf '%s' "$v" | LC_ALL=C tr '\n\t' '  ')"
  done > "$1"
}

# set_env <file> <key> <value>: env.txt의 한 줄을 바꾼다
set_env() {
  awk -v k="$2" -v v="$3" 'BEGIN{FS=OFS="="} $1==k{print k, v; next} {print}' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

# load_jar_env <file>: make jar가 쓴 api-<rev>.env 값을 ENV_<key>로 읽는다
load_jar_env() {
  local k v
  if [ ! -s "$1" ]; then warn "jar env 없음: $1"; return 0; fi
  while IFS='=' read -r k v; do
    case $k in git_rev|git_tree|merge_base|patch_id|dirty|jar|jar_sha256|hibernate_core|elasticsearch_java|search_test_exit)
      printf -v "ENV_$k" '%s' "$v" ;;
    esac
  done < "$1"
}
