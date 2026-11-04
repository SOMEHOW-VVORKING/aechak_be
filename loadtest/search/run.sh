#!/usr/bin/env bash
# 변형 하나의 측정 셀을 순서대로 돌린다. 러너에서 실행하고 --local이면 노트북 compose를 쓴다
# 종료 코드: 0 정상, 3 manifest 불일치, 4 DDL 상태 불일치, 5 api 준비 실패, 6 k6 오류, 7 유휴 대기 초과
# shellcheck disable=SC2034,SC2119  # ENV_ 값은 write_env가 간접 참조로 읽는다
set -euo pipefail
# shellcheck source=lib/common.sh
. "$(cd "$(dirname "$0")" && pwd)/lib/common.sh"

usage() {
  echo 'usage: run.sh --step SEARCH-Txx --dataset 100k|1m|mirror|local --variant NAME --rev REV [--ddl "ids"] [--env "K=V ..."] [--cells "..."] [--base-rate R [--base-source before|manual]] [--force] [--local] [--quick]' >&2
  exit 1
}

ALL_CELLS="smoke ramp steady spike sequential explain"
STEP='' DATASET='' VARIANT='' REV='' DDL='' ENVS='' CELLS=$ALL_CELLS LOCAL=0 QUICK=0 BASE_ARG='' BASE_SRC=manual FORCE=0
while [ $# -gt 0 ]; do
  case $1 in
    --step) STEP=${2:?}; shift 2 ;;
    --dataset) DATASET=${2:?}; shift 2 ;;
    --variant) VARIANT=${2:?}; shift 2 ;;
    --rev) REV=${2:?}; shift 2 ;;
    --ddl) DDL=${2-}; shift 2 ;;
    --env) ENVS=${2-}; shift 2 ;;
    --cells) CELLS=${2:?}; shift 2 ;;
    --base-rate) BASE_ARG=${2:?}; shift 2 ;;
    --base-source) BASE_SRC=${2:?}; shift 2 ;;
    --force) FORCE=1; shift ;;
    --local) LOCAL=1; shift ;;
    --quick) QUICK=1; shift ;;
    *) usage ;;
  esac
done
if [ "$LOCAL" = 1 ] && [ -z "$DATASET" ]; then DATASET=local; fi
case $STEP in SEARCH-T[0-9][0-9]) ;; *) usage ;; esac
case $VARIANT in ''|*[!a-z0-9-]*) usage ;; esac
case $REV in ''|*[!0-9a-z-]*) usage ;; esac
case "$LOCAL/$DATASET" in 1/local|0/100k|0/1m|0/mirror) ;; *) usage ;; esac
if [ "$QUICK" = 1 ] && [ "$LOCAL" != 1 ]; then die 1 "--quick은 --local에서만 쓴다"; fi
for c in $CELLS; do case " $ALL_CELLS " in *" $c "*) ;; *) die 1 "알 수 없는 셀: $c" ;; esac; done
for d in $DDL; do case $d in *[!A-Za-z0-9_-]*) die 1 "DDL id 형식 오류: $d" ;; esac; done
case $BASE_ARG in '') ;; *[!0-9.]*|.|*.*.*) usage ;; esac
case $BASE_SRC in before|manual) ;; *) usage ;; esac

if [ "$QUICK" = 1 ]; then
  RAMP_RATES="2 4 8" RAMP_DUR=10s STEADY_DUR=30s SPIKE_S=10 PROBE_S=20 WARM_DUR=10s SMOKE_DUR=10s
  SEQ_REPS=2 SEQ_KW=class IDLE_HOLD=4 IDLE_SETTLE=2 RAMP_FLOOR_RATES=${RAMP_FLOOR_RATES:-1 0.5}
else
  RAMP_RATES="2 4 8 16 32 64 128 256" RAMP_DUR=45s STEADY_DUR=180s SPIKE_S=30 PROBE_S=90 WARM_DUR=60s SMOKE_DUR=
  IDLE_HOLD=10 IDLE_SETTLE=20 RAMP_FLOOR_RATES=${RAMP_FLOOR_RATES:-1 0.5 0.25}
  case $DATASET in 1m|mirror) SEQ_REPS=3 SEQ_KW=class ;; *) SEQ_REPS=20 SEQ_KW=all ;; esac
fi
# 100만 건은 기준선 첫 페이지 하나가 유휴 상태에서도 10초에 가까워 30초를 쓴다(2026-10-15 기준과 같음)
case $DATASET in 1m) REQ_TIMEOUT_S=30 ;; *) REQ_TIMEOUT_S=10 ;; esac
WARMUP_RATE=${WARMUP_RATE:-2}
IDLE_RDS_CPU_MAX=${IDLE_RDS_CPU_MAX:-5}
MAX_STABLE='' CAPACITY=''

# 1. 환경
resolve_env "$DATASET"
RAW=$OUT/raw CAP=$OUT/captures
mkdir -p "$RAW" "$CAP" "$OUT/jars"
DATE_KST=$(TZ=Asia/Seoul date +%F)
RUN_IDS=
CAPTURES=
PANELS="1 2 3 4 5 6 7 8 9 10 11 12 13 14 17 18 20"
# smoke와 sequential은 k6 지연, k6 처리량, k6 오류, RDS CPU, api CPU만 남긴다
SMALL_PANELS="12 11 13 1 6"
CW_EXTRA=
if [ "$DATASET" = mirror ]; then PANELS="$PANELS 19" CW_EXTRA="CPUCreditBalance CPUSurplusCreditBalance"; fi
if [ -n "$ES_URL" ]; then PANELS="$PANELS 15 16"; fi

ENV_step=$STEP ENV_dataset=$DATASET ENV_variant=$VARIANT ENV_date_kst=$DATE_KST ENV_kit_version=$KIT_VERSION
ENV_rds_id=$RDS_ID ENV_rds_class=$RDS_CLASS ENV_jar=api-$REV.jar ENV_request_timeout=${REQ_TIMEOUT_S}s
ENV_kit_hash=$(kit_hash)
# shortcut: api 상한은 apihost compose 고정값을 적는다. 상한을 변형 변수로 바꾸게 되면 docker inspect로 읽는다
ENV_api_cpus=1 ENV_api_mem=2g
ENV_k6_version=$("$K6_BIN" version 2>/dev/null | awk '{print $2}' || true)
ENV_search_env=$(echo $ENVS | tr ' ' ',')
ENV_applied_ddl=$(echo $DDL | tr ' ' ',')
if [ -z "$ENV_applied_ddl" ]; then ENV_applied_ddl=none; fi
if [ -n "$BASE_ARG" ]; then ENV_base_rate=$BASE_ARG ENV_base_rate_source=$BASE_SRC; fi
# 러너에 GNU time이 있으면 k6 최대 RSS를 잰다
TIME_RSS=0
if [ "$LOCAL" = 0 ] && /usr/bin/time -f %M -o /dev/null true 2>/dev/null; then TIME_RSS=1; fi
PROGRESS=
if [ "$LOCAL" = 0 ]; then
  PROGRESS=$OUT/progress/$DATE_KST-$STEP-$DATASET-$VARIANT.log
  mkdir -p "$OUT/progress"
  : > "$PROGRESS"
fi

# progress <줄>: 표준 출력에 찍고 AWS면 s3 progress/ 로그에도 덧붙인다
progress() {
  echo "$1"
  if [ -z "$PROGRESS" ]; then return 0; fi
  printf '%s\n' "$1" >> "$PROGRESS"
  x aws s3 cp --only-show-errors "$PROGRESS" "s3://$BUCKET/progress/${PROGRESS##*/}" || warn "progress 업로드 실패"
}

if [ "$LOCAL" = 1 ]; then
  load_jar_env "$KIT/jars/api-$REV.env"
else
  x aws s3 cp --only-show-errors "s3://$BUCKET/jars/api-$REV.env" "$OUT/jars/api-$REV.env"
  load_jar_env "$OUT/jars/api-$REV.env"
  tok=$(x curl -sf -X PUT -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' http://169.254.169.254/latest/api/token || true)
  ENV_runner_instance_type=$(x curl -sf -H "X-aws-ec2-metadata-token: $tok" http://169.254.169.254/latest/meta-data/instance-type || true)
  ENV_api_instance_type=$(x aws ec2 describe-instances --instance-ids "$API_ID" \
    --query 'Reservations[0].Instances[0].InstanceType' --output text || true)
fi

rid_of() { echo "$DATE_KST-$STEP-$DATASET-$1-$VARIANT-r$2"; }

# 같은 run id가 이미 있으면 멈춘다. --force(또는 local)이면 그 run id의 raw, 캡처, S3 객체를 지우고 다시 잰다
existing=
if [ -n "$BUCKET" ] && ! is_dry; then
  existing=$(aws s3 ls "s3://$BUCKET/results/$DATE_KST-$STEP-$DATASET-" | awk '$1=="PRE"{print $2}' || true)
fi
for c in warmup $CELLS; do
  n=1; if [ "$c" = steady ]; then n=3; fi
  i=1
  while [ "$i" -le "$n" ]; do
    id=$(rid_of "$c" "$i")
    in_s3=0
    if printf '%s\n' "$existing" | grep -qx "$id/"; then in_s3=1; fi
    if [ "$in_s3" = 1 ] || [ -e "$RAW/$id.env.txt" ] || [ -d "$CAP/$id" ]; then
      [ "$LOCAL" = 1 ] || [ "$FORCE" = 1 ] || die 1 "이미 있는 run id: $id (다시 재려면 FORCE=1)"
      gone=$(cd "$RAW" && ls -d "$id".* 2>/dev/null || true)
      if [ -d "$CAP/$id" ]; then gone="$gone $CAP/$id"; fi
      if [ "$in_s3" = 1 ]; then gone="$gone s3://$BUCKET/results/$id/"; fi
      log "다시 잰다. 지움: $(echo $gone)"
      rm -rf "${RAW:?}/$id".* "${CAP:?}/$id"
      if [ "$in_s3" = 1 ]; then x aws s3 rm --recursive --only-show-errors "s3://$BUCKET/results/$id/"; fi
    fi
    i=$((i + 1))
  done
done

# 2. manifest 확인
if [ "$LOCAL" = 1 ]; then MF=$OUT/manifest/local.json; else MF=$KIT/results/manifest/$DATASET.json; fi
# 거울층은 10만 건 기준 스냅샷에서 복원하므로 10만 건 manifest와 비교한다
if [ "$DATASET" = mirror ]; then MF=$KIT/results/manifest/100k.json; fi
if is_dry; then
  sql < "$KIT/sql/manifest.sql"
  log "DRY: manifest 비교 생략 ($MF)"
else
  [ -s "$MF" ] || die "$E_MANIFEST" "manifest 없음: $MF (make init 또는 make manifest 먼저)"
  want=$(jq -r '(.rows | to_entries[] | "rows.\(.key)\t\(.value)"), (.crc | to_entries[] | "crc.\(.key)\t\(.value)")' "$MF" | LC_ALL=C sort)
  have=$(sql < "$KIT/sql/manifest.sql" | grep -E '^(rows|crc)\.' | LC_ALL=C sort || true)
  if [ "$want" != "$have" ]; then
    diff <(printf '%s\n' "$want") <(printf '%s\n' "$have") >&2 || true
    die "$E_MANIFEST" "행 수나 체크섬이 manifest와 다르다. make reseed DATASET=$DATASET"
  fi
fi
ENV_manifest_match=yes

# 3. DDL 상태: 적용된 id가 --ddl의 앞부분과 같아야 하고 나머지는 순서대로 적용한다
echo 'CREATE TABLE IF NOT EXISTS perf_applied_ddl (id VARCHAR(64) NOT NULL PRIMARY KEY, sha256 CHAR(64) NOT NULL, applied_at DATETIME(6) NOT NULL)' | sql
applied=$(echo 'SELECT id, sha256 FROM perf_applied_ddl ORDER BY applied_at, id' | sql)
i=0
while read -r id sha; do
  if [ -z "$id" ]; then continue; fi
  i=$((i + 1))
  if [ "$id" != "$(nth "$DDL" "$i")" ]; then
    die "$E_DDL" "적용된 DDL ${id}가 --ddl \"$DDL\"과 맞지 않는다. results/rollback/$id.sql로 되돌린 뒤 다시"
  fi
  if [ "$(sha256_file "$KIT/ddl/$id.sql")" != "$sha" ]; then die "$E_DDL" "ddl/$id.sql이 적용한 뒤 바뀌었다"; fi
done <<<"$applied"
j=0
for id in $DDL; do
  j=$((j + 1))
  if [ "$j" -le "$i" ]; then continue; fi
  f=$KIT/ddl/$id.sql
  [ -f "$f" ] || die "$E_DDL" "ddl/$id.sql 없음"
  log "DDL 적용 $id"
  sql < "$f" || die "$E_DDL" "ddl/$id.sql 적용 실패"
  printf "INSERT INTO perf_applied_ddl VALUES ('%s', '%s', NOW(6))\n" "$id" "$(sha256_file "$f")" | sql
done
ENV_schema_hash=$(sql <<'SQL' | sha256
SELECT table_name, column_name, ordinal_position, column_type, is_nullable, IFNULL(column_default, 'NULL'), extra
FROM information_schema.columns WHERE table_schema = 'aechak' AND table_name <> 'perf_applied_ddl'
ORDER BY table_name, ordinal_position
SQL
)
ENV_index_hash=$(sql <<'SQL' | sha256
SELECT table_name, index_name, seq_in_index, column_name, non_unique, index_type, IFNULL(sub_part, '')
FROM information_schema.statistics WHERE table_schema = 'aechak' AND table_name <> 'perf_applied_ddl'
ORDER BY table_name, index_name, seq_in_index
SQL
)
log "warm set"
sql < "$KIT/sql/warm.sql" >/dev/null
IFS=$'\t' read -r ENV_rds_engine_version ENV_buffer_pool_size_bytes ENV_param_max_digest_length \
  ENV_param_perf_schema_max_digest_length ENV_param_long_query_time <<<"$(
  echo 'SELECT VERSION(), @@innodb_buffer_pool_size, @@max_digest_length, @@performance_schema_max_digest_length, @@long_query_time' | sql)"
if [ "$LOCAL" = 1 ]; then local_slow_init; fi

# run 사이 유휴 조건. RDS CPU는 1분에서 3분 늦게 들어와 여기서 보지 않고 변형 시작에 한 번만 본다
idle_ok() {
  local v
  v=$(sql <<'SQL' || true
SELECT COUNT(*) FROM information_schema.processlist
WHERE command <> 'Sleep' AND id <> CONNECTION_ID() AND user NOT IN ('exporter', 'rdsadmin', 'event_scheduler', 'system user')
SQL
)
  IDLE_WHY="active=${v:-?}"
  [ "${v:-x}" = 0 ] || return 1
  v=$(prom_query 'max(hikaricp_connections_pending{job="spring"})' 2>/dev/null || true)
  IDLE_WHY="$IDLE_WHY hikari_pending=${v:-none}"
  [ "$v" = 0 ] || return 1
  if [ -n "$ES_URL" ]; then
    v=$(curl -sf --max-time 5 "$ES_URL/_nodes/stats/thread_pool" | jq '[.nodes[].thread_pool.search.active] | add' || true)
    IDLE_WHY="$IDLE_WHY es_search_active=${v:-?}"
    [ "$v" = 0 ] || return 1
  fi
}

# 조건이 IDLE_HOLD초 연속 맞으면 IDLE_SETTLE초 더 쉰다. 10분을 넘으면 종료 코드 7
idle_wait() {
  local t0 now since=0
  if is_dry; then log "DRY: idle wait"; return 0; fi
  t0=$(date +%s)
  while :; do
    now=$(date +%s)
    if idle_ok; then
      if [ "$since" = 0 ]; then since=$now; fi
      if [ $((now - since)) -ge "$IDLE_HOLD" ]; then sleep "$IDLE_SETTLE"; return 0; fi
    else
      since=0
    fi
    if [ $((now - t0)) -ge 600 ]; then die "$E_IDLE" "유휴 대기 10분 초과: $IDLE_WHY"; fi
    sleep 2
  done
}

# 최신 RDS CPU 1분 평균이 IDLE_RDS_CPU_MAX 아래가 될 때까지 30초마다 본다. 값이 없으면 계속 보고 10분을 넘으면 종료 코드 7
rds_idle_wait() {
  local t0 v
  if [ "$LOCAL" = 1 ] || is_dry; then return 0; fi
  t0=$(date +%s)
  while :; do
    v=$(aws cloudwatch get-metric-statistics --namespace AWS/RDS --metric-name CPUUtilization \
      --dimensions Name=DBInstanceIdentifier,Value="$RDS_ID" --start-time "$(iso_of_ms $(( $(now_ms) - 300000 )))" \
      --end-time "$(iso_of_ms "$(now_ms)")" --period 60 --statistics Average \
      --query 'sort_by(Datapoints, &Timestamp)[-1].Average' --output text 2>/dev/null || echo None)
    case $v in
      None|null|'') log "RDS CPU 1분 지표가 없어 다시 본다" ;;
      *)
        if awk -v c="$v" -v m="$IDLE_RDS_CPU_MAX" 'BEGIN{exit !(c < m)}'; then log "RDS CPU ${v}% 유휴"; return 0; fi
        log "RDS CPU ${v}%, ${IDLE_RDS_CPU_MAX}% 아래를 기다린다" ;;
    esac
    if [ $(( $(date +%s) - t0 )) -ge 600 ]; then die "$E_IDLE" "RDS CPU 유휴 대기 10분 초과: ${v}"; fi
    sleep 30
  done
}

bp_counters() {
  echo "SELECT MAX(IF(variable_name = 'Innodb_buffer_pool_reads', variable_value, NULL)), MAX(IF(variable_name = 'Innodb_buffer_pool_read_requests', variable_value, NULL)) FROM performance_schema.global_status" | sql
}

dump_digest() {
  printf 'rank\tcount_star\tsum_timer_wait_ms\tavg_timer_wait_ms\tsum_rows_examined\tsum_sort_rows\tsum_rows_sent\tdigest_text\n'
  sql <<'SQL'
SELECT ROW_NUMBER() OVER (ORDER BY sum_timer_wait DESC), count_star, ROUND(sum_timer_wait / 1e9, 3), ROUND(avg_timer_wait / 1e9, 3),
       sum_rows_examined, sum_sort_rows, sum_rows_sent, digest_text
FROM performance_schema.events_statements_summary_by_digest
WHERE schema_name = 'aechak' AND digest_text NOT LIKE '%performance_schema%' AND digest_text NOT LIKE '%information_schema%' AND digest_text NOT LIKE 'SELECT @@%'
ORDER BY sum_timer_wait DESC LIMIT 10
SQL
}

new_run() {  # $1 cell $2 rep: run id를 정하고 run마다 달라지는 ENV_ 값을 비운다
  RID=$(rid_of "$1" "$2")
  ENV_run_id=$RID ENV_cell=$1 ENV_rep=$2 ENV_req_rate='' ENV_scenario='' ENV_max_vus='' ENV_dropped_iterations=''
  ENV_k6_exit='' ENV_k6_max_rss_kb='' ENV_skip_reason='' ENV_start_ms='' ENV_end_ms=''
  ENV_bp_reads_delta='' ENV_bp_read_requests_delta='' ENV_bp_disk_read_flag='' ENV_probe_rate='' ENV_trace_offset=''
}

begin_run() {  # $1 cell $2 rep
  new_run "$1" "$2"
  log "== $RID"
  idle_wait
  echo 'TRUNCATE performance_schema.events_statements_summary_by_digest' | sql
  BP0=$(bp_counters)
  SLOW0=0
  if [ "$LOCAL" = 1 ]; then SLOW0=$(local_slow_size); fi
  ENV_start_ms=$(now_ms)
}

# end_run <full|small|none>: CloudWatch와 slow log는 임시로 받고 캡처는 변형 끝에서 한꺼번에 찍는다
end_run() {
  ENV_end_ms=$(now_ms)
  local bp1; bp1=$(bp_counters)
  set -- "$1" $BP0 $bp1
  if [ $# = 5 ]; then
    ENV_bp_reads_delta=$(( $4 - $2 )) ENV_bp_read_requests_delta=$(( $5 - $3 ))
    ENV_bp_disk_read_flag=0
    if [ "$ENV_bp_reads_delta" -gt 0 ]; then ENV_bp_disk_read_flag=1; warn "$RID buffer pool 디스크 읽기 $ENV_bp_reads_delta"; fi
  fi
  if [ -z "${ENV_hikari_max:-}" ]; then ENV_hikari_max=$(prom_query 'max(hikaricp_connections_max{job="spring"})' 2>/dev/null || true); fi
  ENV_max_stable_rate=$MAX_STABLE ENV_capacity_rate=$CAPACITY
  dump_digest > "$RAW/$RID.digest.txt"
  cw_fetch "$RDS_ID" "$ENV_start_ms" "$ENV_end_ms" "$RAW/$RID.cw.csv" $CW_EXTRA
  if [ "$LOCAL" = 1 ]; then local_slow_copy "$SLOW0" "$RAW/$RID.slow.log"
  else slowlog_fetch "$RDS_ID" "$ENV_start_ms" "$ENV_end_ms" "$RAW/$RID.slow.log"; fi
  prom_export "$ENV_start_ms" "$ENV_end_ms" > "$RAW/$RID.prom.json" || warn "Prometheus 지표 저장 실패: $RID"
  write_env "$RAW/$RID.env.txt"
  case $1 in full|small) CAPTURES="$CAPTURES $RID:$1" ;; esac
  upload_run "$RID"
  RUN_IDS="$RUN_IDS $RID"
  progress "RUN_ID $RID"
}

# skip_cell <cell> <이유>: 셀을 건너뛰었다는 env.txt만 남겨 summarize가 건너뛴 행으로 보이게 한다
skip_cell() {
  new_run "$1" 1
  ENV_skip_reason=$2
  warn "$RID 건너뜀: $2"
  write_env "$RAW/$RID.env.txt"
  upload_run "$RID"
  RUN_IDS="$RUN_IDS $RID"
  progress "RUN_ID $RID"
}

# k6_run <출력 접두어> [KEY=VALUE | --out=X]...: search.js를 한 번 돌리고 K6_EXIT, K6_RSS를 남긴다
# 종료 코드 99(실패율 안전 중단)는 기록만 하고 계속한다
k6_run() {
  local pre=$1 a c
  shift
  K6_EXIT=0 K6_RSS=''
  c=(env "K6_PROMETHEUS_RW_SERVER_URL=$PROM_URL/api/v1/write" "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),max"
     "$K6_BIN" run --quiet --tag "testid=$RID" -o "$K6_PROM_OUT"
     -e "BASE_URL=$BASE_URL" -e "RUN_ID=$RID" -e "SUMMARY_FILE=$pre.json" -e "TEXT_SUMMARY_FILE=$pre.k6.txt"
     -e "REQUEST_TIMEOUT=${REQ_TIMEOUT_S}s")
  if [ "$TIME_RSS" = 1 ]; then c=(/usr/bin/time -f %M -o "$pre.rss" "${c[@]}"); fi
  for a in "$@"; do
    case $a in
      --out=*) c+=(--out "${a#--out=}") ;;
      *) c+=(-e "$a") ;;
    esac
  done
  c+=("$KIT/search.js")
  x "${c[@]}" >&2 || K6_EXIT=$?
  if [ -s "$pre.rss" ]; then K6_RSS=$(tail -n 1 "$pre.rss"); fi
  rm -f "$pre.rss"
  case $K6_EXIT in 0) ;; 99) warn "k6 안전 중단(실패율 50% 이상) $RID" ;; *) die "$E_K6" "k6 종료 코드 $K6_EXIT ($RID)" ;; esac
}

summary_dropped() {
  if [ -s "$1" ]; then jq -r '.metrics.dropped_iterations.values.count // 0' "$1"; else echo na; fi
}

# measure <cell> <rep> <full|small|none 캡처> [k6 인자...]: 측정 run 하나
measure() {
  local cell=$1 rep=$2 cap=$3 a
  shift 3
  begin_run "$cell" "$rep"
  for a in "$@"; do
    case $a in
      SCENARIO=*) ENV_scenario=${a#*=} ;;
      REQ_RATE=*) ENV_req_rate=${a#*=} ;;
      MAX_VUS=*) ENV_max_vus=${a#*=} ;;
      PROBE_RATE=*) ENV_probe_rate=${a#*=} ;;
      TRACE_OFFSET=*) ENV_trace_offset=${a#*=} ;;
    esac
  done
  k6_run "$RAW/$RID" "$@"
  ENV_k6_exit=$K6_EXIT ENV_k6_max_rss_kb=$K6_RSS
  ENV_dropped_iterations=$(summary_dropped "$RAW/$RID.json")
  end_run "$cap"
}

# ramp_stage <요청률> [길이]: 단계 하나를 돌려 .ramp.tsv에 한 줄을 붙인다. SLO를 지키면 0
# 실패율 1% 미만, dropped 0, k6 종료 0(처리 기준)이면 RAMP_CAP_OK=1
ramp_stage() {
  local rate=$1 dur=${2:-$RAMP_DUR} row s e d
  s=$(now_ms)
  k6_run "$RAW/$RID.s$rate" SCENARIO=rate "REQ_RATE=$rate" "DURATION=$dur" "MAX_VUS=$(max_vus "$rate")"
  e=$(now_ms)
  row=$(k6_eval "$RAW/$RID.s$rate.json")
  if is_dry && [ ! -s "$RAW/$RID.s$rate.json" ]; then row="na	na	na	0	0	1"; fi
  # 안전 중단된 단계는 실패로 본다
  if [ "$K6_EXIT" = 99 ]; then row=$(printf '%s\n' "$row" | awk 'BEGIN{FS=OFS="\t"} {$6=0; print}'); fi
  printf '%s\t%s\t%s\t%s\t%s\n' "$rate" "$row" "$s" "$e" "$K6_EXIT" >> "$RAMP_TSV"
  RAMP_CAP_OK=$(printf '%s\n' "$row" | awk -F'\t' -v k="$K6_EXIT" '{print ($4 != "na" && $4 < 1 && $5 == 0 && k == 0) ? 1 : 0}')
  RAMP_RAN="$RAMP_RAN $rate"
  d=$(printf '%s' "$row" | cut -f5)
  case $d in na) ;; *) RAMP_DROPPED=$((RAMP_DROPPED + d)) ;; esac
  if [ -n "$K6_RSS" ] && [ "$K6_RSS" -gt "${ENV_k6_max_rss_kb:-0}" ]; then ENV_k6_max_rss_kb=$K6_RSS; fi
  log "ramp $rate req/s: $row k6_exit=$K6_EXIT"
  [ "$(printf '%s' "$row" | cut -f6)" = 1 ]
}

# floor_dur <요청률>: 반복이 20회 이상 들도록 늘린 바닥 단계 길이. 낮은 요청률의 45초에는 검색어 등급이 다 들지 않는다
# 분당 반복 수는 search.js의 iterRate처럼 반올림하고 1.4는 search.js의 REQS_PER_MIXED_ITER다(ramp는 늘 MIX=mixed)
floor_dur() {
  awk -v r="$1" -v d="${RAMP_DUR%s}" 'BEGIN{m = int(r * 60 / 1.4 + 0.5); if (m < 1) m = 1; s = 20 * 60 / m; if (s > int(s)) s = int(s) + 1; print (s > d ? s : d) "s"}'
}

# RAMP_RATES를 올라가며 처리 기준을 처음 못 지킨 단계에서 멈춘다. 첫 SLO 실패 전까지 SLO를 지킨 마지막 단계가 최대 안정 요청률이다
# 첫 단계부터 SLO를 못 지키면(처리 기준 통과 여부와 무관) RAMP_FLOOR_RATES를 내려가며 SLO를 처음 지킨 단계에서 멈추고 그 단계가 최대 안정 요청률이다
# 처리 가능 요청률은 돈 단계 중 p95를 빼고 실패율 1% 미만, dropped 0, k6 종료 0인 가장 높은 단계다
cell_ramp() {
  local rate t slo=1
  begin_run ramp 1
  RAMP_TSV=$RAW/$RID.ramp.tsv RAMP_RAN='' RAMP_DROPPED=0
  printf 'rate\thttp_reqs_per_s\tfirst_p95_ms\tfirst_p99_ms\tfailed_pct\tdropped\tpass\tstart_ms\tend_ms\tk6_exit\n' > "$RAMP_TSV"
  MAX_STABLE=0
  for rate in $RAMP_RATES; do
    if ramp_stage "$rate"; then
      if [ "$slo" = 1 ]; then MAX_STABLE=$rate; fi
    else
      slo=0
    fi
    [ "$RAMP_CAP_OK" = 1 ] || break
  done
  if [ "$MAX_STABLE" = 0 ]; then
    for rate in $RAMP_FLOOR_RATES; do
      # 바닥 단계마다 앞 단계가 남긴 쿼리와 Hikari 대기가 빠질 때까지 유휴 대기를 한다
      t=$SECONDS
      idle_wait
      log "ramp 바닥 단계 $rate req/s 전 유휴 대기 $((SECONDS - t))s"
      if ramp_stage "$rate" "$(floor_dur "$rate")"; then MAX_STABLE=$rate; break; fi
    done
  fi
  CAPACITY=$(awk -F'\t' 'NR > 1 && $5 != "na" && $5 < 1 && $6 == 0 && $10 == 0 && $1 > c {c = $1} END {print c + 0}' "$RAMP_TSV")
  tail -n +2 "$RAMP_TSV" | while IFS=$'\t' read -r rate _ _ _ _ _ _ s e k; do
    if [ -s "$RAW/$RID.s$rate.json" ]; then
      jq -c --arg r "$rate" --argjson s "$s" --argjson e "$e" --argjson k "$k" \
        '{($r): (. + {start_ms: $s, end_ms: $e, k6_exit: $k})}' "$RAW/$RID.s$rate.json"
    fi
  done | jq -s --argjson ms "$MAX_STABLE" '{max_stable_rate: $ms, stages: (add // {})}' > "$RAW/$RID.json"
  # env.txt의 k6_exit는 최대 안정 단계 값, SLO를 통과한 단계가 없으면 마지막 단계 값
  ENV_k6_exit=$(awk -F'\t' -v m="$MAX_STABLE" 'NR > 1 {last = $10; if ($1 == m) hit = $10} END {print (hit != "" ? hit : last)}' "$RAMP_TSV")
  ENV_scenario=rate ENV_req_rate=$(echo $RAMP_RAN | tr ' ' ',') ENV_dropped_iterations=$RAMP_DROPPED
  # 기준 요청률은 최대 안정 요청률, 그것이 0이면 처리 가능 요청률(비교 부하)이다
  if [ -z "$BASE_ARG" ]; then
    if awk -v b="$MAX_STABLE" 'BEGIN{exit !(b > 0)}'; then ENV_base_rate=$MAX_STABLE ENV_base_rate_source=own
    elif awk -v b="$CAPACITY" 'BEGIN{exit !(b > 0)}'; then ENV_base_rate=$CAPACITY ENV_base_rate_source=capacity
    else ENV_base_rate=0 ENV_base_rate_source=own; fi
  fi
  log "max_stable_rate=$MAX_STABLE capacity_rate=$CAPACITY"
  end_run full
}

# base_rate: --base-rate가 있으면 그 값, 없으면 cell_ramp가 정한 기준 요청률을 BASE에 둔다. 0이면 1을 돌려준다
base_rate() {
  if [ -n "$BASE_ARG" ]; then
    BASE=$BASE_ARG SKIP_WHY="기준 요청률 0 (--base-rate)"
  else
    [ -n "$MAX_STABLE" ] || die 1 "기준 요청률을 모른다. ramp 셀을 같이 돌리거나 --base-rate(BASE_RATE)를 준다"
    BASE=$ENV_base_rate SKIP_WHY="기준 요청률 0 (ramp에서 SLO도 처리 기준도 통과한 단계가 없다)"
  fi
  awk -v b="$BASE" 'BEGIN{exit !(b > 0)}'
}

# cw_wait <ms>: 그 시각이 든 분의 RDS CPU 1분 지표가 생길 때까지 30초마다 본다. 6분이 지나면 그만둔다
cw_wait() {
  local m n t0
  if is_dry; then return 0; fi
  m=$(( $1 / 60000 * 60000 )) t0=$(date +%s)
  while :; do
    n=$(aws cloudwatch get-metric-statistics --namespace AWS/RDS --metric-name CPUUtilization \
      --dimensions Name=DBInstanceIdentifier,Value="$RDS_ID" --start-time "$(iso_of_ms "$m")" \
      --end-time "$(iso_of_ms $((m + 60000)))" --period 60 --statistics Average \
      --query 'length(Datapoints)' --output text 2>/dev/null || echo 0)
    if [ "${n:-0}" -gt 0 ] 2>/dev/null; then return 0; fi
    if [ $(( $(date +%s) - t0 )) -ge 360 ]; then warn "CloudWatch 지표가 6분 안에 다 들어오지 않았다"; return 0; fi
    sleep 30
  done
}

# 변형 끝. CloudWatch가 마지막 run이 끝난 분까지 들어오면 run마다 지표와 slow log를 다시 받고 캡처를 찍어 올린다
finish_variant() {
  local last rid s e item t0
  if [ "$LOCAL" = 0 ]; then
    last=$(for rid in $RUN_IDS; do sed -n 's/^end_ms=//p' "$RAW/$rid.env.txt"; done | grep -E '^[0-9]+$' | sort -n | tail -n 1 || true)
    if [ -n "$last" ]; then cw_wait "$last"; fi
    for rid in $RUN_IDS; do
      s=$(sed -n 's/^start_ms=//p' "$RAW/$rid.env.txt") e=$(sed -n 's/^end_ms=//p' "$RAW/$rid.env.txt")
      case "$s/$e" in *[!0-9/]*|/*|*/) continue ;; esac
      cw_fetch "$RDS_ID" "$s" "$e" "$RAW/$rid.cw.csv" $CW_EXTRA
      slowlog_fetch "$RDS_ID" "$s" "$e" "$RAW/$rid.slow.log"
      if is_dry; then continue; fi
      if grep -q "^$(iso_of_ms $(( e / 60000 * 60000 )) | cut -c1-16).*,CPUUtilization,Average," "$RAW/$rid.cw.csv"; then
        set_env "$RAW/$rid.env.txt" cw_complete true
      else
        set_env "$RAW/$rid.env.txt" cw_complete false
      fi
    done
  fi

  # 캡처는 지표가 다 들어온 뒤 한꺼번에 찍는다. ramp, steady, spike는 전체 패널, smoke와 sequential은 다섯 패널
  for item in $CAPTURES; do
    rid=${item%:*} t0=$SECONDS
    s=$(sed -n 's/^start_ms=//p' "$RAW/$rid.env.txt") e=$(sed -n 's/^end_ms=//p' "$RAW/$rid.env.txt")
    if [ "${item##*:}" = full ]; then set -- --panels "$PANELS"; else set -- --panels "$SMALL_PANELS" --no-dashboard; fi
    x bash "$KIT/capture.sh" --run-id "$rid" --from "$s" --to "$e" --dbid "$RDS_ID" --out "$CAP/$rid" \
      --grafana "$GRAFANA_URL" "$@" >&2 || warn "capture.sh 실패: $rid"
    log "capture $rid $((SECONDS - t0))s"
  done
  for rid in $RUN_IDS; do upload_run "$rid"; done
  progress VARIANT_DONE
}

# 끝난 run이 하나라도 있으면 정상 종료든 중간 종료든 변형 끝 처리를 한 번 하고 원래 종료 코드로 끝낸다
on_exit() {
  local rc=$? frc
  trap - EXIT
  set +e
  if [ -n "$RUN_IDS" ]; then
    (set -e; finish_variant)
    frc=$?
    if [ "$frc" != 0 ]; then warn "변형 끝 처리 실패(종료 코드 ${frc}). run.sh 종료 코드는 그대로 ${rc}"; fi
  fi
  exit "$rc"
}
trap on_exit EXIT

# 4. api 재시작과 워밍업
set -- --rev "$REV" --dataset "$DATASET"
for kv in $ENVS; do set -- "$@" --env "$kv"; done
log "api 재시작 rev=$REV"
restart_api "$@" || die "$E_API" "api가 준비되지 않았다"
rds_idle_wait
# explain 셀이 켜는 history_long consumer를 변형마다 끈 상태로 시작해 before와 after의 관측 부하를 맞춘다
echo "UPDATE performance_schema.setup_consumers SET enabled = 'NO' WHERE name = 'events_statements_history_long'" | sql || true
ENV_ps_history_long=$(echo "SELECT enabled FROM performance_schema.setup_consumers WHERE name = 'events_statements_history_long'" | sql || true)
if ! is_dry && [ "$ENV_ps_history_long" != NO ]; then die 1 "events_statements_history_long consumer를 끄지 못했다(${ENV_ps_history_long:-읽기 실패})"; fi
measure warmup 1 none SCENARIO=rate "REQ_RATE=$WARMUP_RATE" "DURATION=$WARM_DUR" "MAX_VUS=$(max_vus "$WARMUP_RATE")"

# 5. 셀
for c in $ALL_CELLS; do
  case " $CELLS " in *" $c "*) ;; *) continue ;; esac
  case $c in
    smoke)
      if [ -n "$SMOKE_DUR" ]; then measure smoke 1 small SCENARIO=smoke "DURATION=$SMOKE_DUR"; else measure smoke 1 small SCENARIO=smoke; fi ;;
    ramp) cell_ramp ;;
    steady)
      if ! base_rate; then skip_cell steady "$SKIP_WHY"; continue; fi
      r=$(mul "$BASE" 0.7)
      # 반복마다 다른 고정 순서를 써서 세 run의 폭에 요청 구성의 차이도 들게 한다. before와 after는 같은 반복끼리 순서가 같다
      for rep in 1 2 3; do
        measure steady "$rep" full SCENARIO=rate "REQ_RATE=$r" "DURATION=$STEADY_DUR" "MAX_VUS=$(max_vus "$r")" "TRACE_OFFSET=$(( (rep - 1) * 100000 ))"
      done ;;
    spike)
      if ! base_rate; then skip_cell spike "$SKIP_WHY"; continue; fi
      # 탐침은 기준 요청률의 절반으로 보내고 2 req/s를 넘기지 않는다
      r=$(mul "$BASE" 3) pr=$(awk -v b="$BASE" 'BEGIN{p = b * 0.5; printf "%g\n", (p < 2 ? p : 2)}')
      measure spike 1 full SCENARIO=spike "REQ_RATE=$r" "SPIKE_SECONDS=$SPIKE_S" "PROBE_RATE=$pr" "PROBE_SECONDS=$PROBE_S" \
        "MAX_VUS=$(max_vus "$r")" "--out=csv=$RAW/$(rid_of spike 1).points.csv.gz"
      if [ "$ENV_dropped_iterations" != 0 ] && [ "$ENV_dropped_iterations" != na ]; then
        warn "SPIKE_DROPPED $RID dropped_iterations=$ENV_dropped_iterations (제공 부하가 3배에 못 미쳤다)"
      fi ;;
    sequential) measure sequential 1 small SCENARIO=sequential "SEQ_REPS=$SEQ_REPS" "SEQ_KEYWORDS=$SEQ_KW" ;;
    explain)
      begin_run explain 1
      if [ "$LOCAL" = 1 ]; then set -- --local; else set --; fi
      x "$KIT/explain.sh" --out "$RAW/$RID.explain.txt" "$@" || warn "explain.sh 실패"
      end_run none ;;
  esac
done

