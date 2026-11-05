#!/usr/bin/env bash
# Makefile 타깃을 노트북에서 실행한다. 사용법: lib/laptop.sh <target>, 인자는 Makefile 변수(환경변수)로 받는다
set -euo pipefail
# shellcheck source=lib/common.sh
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

ROOT=$(cd "$KIT/../.." && pwd)
PERF_DIR=${PERF_DIR:-$KIT/.perf}
OUTPUTS=${OUTPUTS:-$PERF_DIR/outputs.json}
export AWS_PROFILE=${AWS_PROFILE:-aechak} AWS_REGION=${AWS_REGION:-ap-northeast-2}
export AWS_DEFAULT_REGION=$AWS_REGION
LOCAL=${LOCAL:-0}
RUNNER_SCRIPTS=/opt/perf/kit/search

need_outputs() {
  if [ ! -s "$OUTPUTS" ] || [ -z "$(jq -r '.runner.value.instance_id // empty' "$OUTPUTS")" ]; then
    die 1 "terraform outputs가 없다($OUTPUTS). make outputs 먼저"
  fi
  BUCKET=$(jq -r .bucket.value "$OUTPUTS")
  RUNNER_ID=$(jq -r .runner.value.instance_id "$OUTPUTS")
  API_ID=$(jq -r .api.value.instance_id "$OUTPUTS")
  ES_ID=$(jq -r '.es.value.instance_id // empty' "$OUTPUTS")
}

db_id_of() {
  local id
  if [ "$1" = mirror ]; then id=$(jq -r '.mirror.value.id // empty' "$OUTPUTS"); else id=$(jq -r --arg d "$1" '.rds.value[$d].id // empty' "$OUTPUTS"); fi
  [ -n "$id" ] || die 1 "outputs에 $1 DB가 없다"
  echo "$id"
}

# 원격 명령 인자를 셸 문자열로 이어 붙인다
q() { local a s=; for a in "$@"; do s="$s $(printf '%q' "$a")"; done; echo "${s# }"; }
on_runner() { ssm_run "$RUNNER_ID" "/opt/perf/bin/kit-pull && $1" "${2:-3600}" "${3:-}"; }
# 호스트를 켠 직후에는 user_data가 끝나기 전에 SSM이 먼저 붙어서 cloud-init이 끝나기를 기다린다
INIT_WAIT='cloud-init status --wait >/dev/null || true; '

# loadtest/ 밖의 변경(추적 안 하는 파일 포함)
dirty_list() { git -C "$ROOT" status --porcelain --untracked-files=all -- . ':(exclude)loadtest'; }
dirty_hash() {
  {
    git -C "$ROOT" diff HEAD -- . ':(exclude)loadtest'
    git -C "$ROOT" ls-files --others --exclude-standard -- . ':(exclude)loadtest'
    git -C "$ROOT" ls-files --others --exclude-standard -- . ':(exclude)loadtest' | git -C "$ROOT" hash-object --stdin-paths
  } | sha256 | cut -c1-8
}

# dep_ver <artifact> <dependencies 출력>: 'a:1.0', 'a:1.0 -> 1.1', 'a -> 1.1' 모두에서 실제 버전을 뽑는다
dep_ver() {
  printf '%s\n' "$2" | grep -m1 -o ":$1\\(:[^ ]*\\)\\{0,1\\}\\( -> [^ ]*\\)\\{0,1\\}" | awk '{print $NF}' | sed 's/.*://' || true
}

# 이미 만든 rev 문자열은 그대로, 커밋 이름은 sha 12자로 바꾼다
variant_rev() {
  case $1 in
    [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]|*-dirty-*) echo "$1" ;;
    *) git -C "$ROOT" rev-parse --verify "$1^{commit}" | cut -c1-12 ;;
  esac
}

need_jar() {
  if [ "$LOCAL" = 1 ]; then
    [ -s "$KIT/jars/api-$1.jar" ] || is_dry || die 1 "jars/api-$1.jar 없음. make jar LOCAL=1 REV=..."
  else
    need_outputs
    x aws s3 ls "s3://$BUCKET/jars/api-$1.jar" >/dev/null || die 1 "S3에 jars/api-$1.jar 없음. make jar REV=..."
    x aws s3 ls "s3://$BUCKET/jars/api-$1.env" >/dev/null || die 1 "S3에 jars/api-$1.env 없음. make jar REV=..."
  fi
}

perf_rds() {
  x aws rds describe-db-instances --output text \
    --query "DBInstances[?TagList[?Key=='Project' && Value=='aechak-perf']].[DBInstanceIdentifier,DBInstanceStatus]"
}

wait_ssm() {
  local id st n
  if is_dry; then return 0; fi
  for id in "$@"; do
    n=0
    while :; do
      st=$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$id" \
        --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)
      if [ "$st" = Online ]; then break; fi
      n=$((n + 1)); [ "$n" -lt 60 ] || die 1 "$id SSM 연결 안 됨"
      sleep 5
    done
  done
}

fetch_run() {
  x aws s3 cp --recursive --only-show-errors "s3://$BUCKET/results/$1/raw/" "$KIT/results/raw/"
  x aws s3 cp --recursive --only-show-errors "s3://$BUCKET/results/$1/captures/" "$KIT/results/captures/$1/"
}

summarize() {  # $1 raw 디렉터리 $2 출력 파일, 나머지 run id. STEP_NOTE가 있으면 블록 머리에 붙이고 STEP_REF의 run id는 기준으로만 읽는다
  local raw=$1 out=$2
  shift 2
  if [ $# = 0 ]; then return 0; fi
  set -- --runs "$@"
  # shellcheck disable=SC2086  # STEP_REF는 공백으로 나눈 run id 목록이다
  if [ -n "${STEP_REF:-}" ]; then set -- "$@" --ref $STEP_REF; fi
  x python3 -I "$KIT/tools/summarize.py" --step "$STEP" --raw "$raw" --out "$out" ${STEP_NOTE:+--note "$STEP_NOTE"} "$@"
}

# rds_status <db-id>: DRY_RUN이면 DRY_RDS_STATUS(기본 stopped)를 돌려준다
rds_status() {
  if is_dry; then echo "${DRY_RDS_STATUS:-stopped}"; return 0; fi
  aws rds describe-db-instances --db-instance-identifier "$1" --query 'DBInstances[0].DBInstanceStatus' --output text
}

# rds_start <db-id>: 멈춰 있거나 멈추는 중이면 켜고, 켜는 중이거나 켜져 있으면 그대로 둔다
rds_start() {
  local st n=0
  st=$(rds_status "$1")
  case $st in
    stopping)
      log "$1 멈추는 중. stopped가 되면 켠다"
      while [ "$st" = stopping ]; do
        n=$((n + 1)); [ "$n" -le 120 ] || die 1 "$1 이 30분 안에 멈추지 않았다"
        if is_dry; then st=stopped; else sleep 15; st=$(rds_status "$1"); fi
      done
      [ "$st" = stopped ] || die 1 "$1 상태가 ${st}로 바뀌었다. 잠시 뒤 make up 다시"
      x aws rds start-db-instance --db-instance-identifier "$1" >/dev/null ;;
    stopped) x aws rds start-db-instance --db-instance-identifier "$1" >/dev/null ;;
    starting|available) ;;
    *) die 1 "$1 상태가 ${st}라 켤 수 없다. 콘솔에서 상태를 보고 다시" ;;
  esac
}

cmd_outputs() {
  local tmp=$OUTPUTS.tmp
  mkdir -p "$PERF_DIR"
  terraform -chdir="$ROOT/loadtest/terraform" output -json > "$tmp"
  [ -n "$(jq -r '.bucket.value // empty' "$tmp")" ] || { rm -f "$tmp"; die 1 "terraform output이 비었다. apply 전인지 확인"; }
  mv "$tmp" "$OUTPUTS"
  x aws s3 cp --only-show-errors "$OUTPUTS" "s3://$(jq -r .bucket.value "$OUTPUTS")/state/outputs.json"
  log "outputs: $OUTPUTS"
}

cmd_jar() {
  local sha rev dir=$ROOT tmp='' dirty=0 deps hib es te jar env
  sha=$(git -C "$ROOT" rev-parse --verify "${REV:-HEAD}^{commit}")
  rev=$(echo "$sha" | cut -c1-12)
  if [ "$sha" = "$(git -C "$ROOT" rev-parse HEAD)" ]; then
    if [ -n "$(dirty_list)" ]; then
      [ "${ALLOW_DIRTY:-0}" = 1 ] || { dirty_list >&2; die 1 "loadtest/ 밖에 커밋하지 않은 변경이 있다. 커밋하거나 ALLOW_DIRTY=1"; }
      rev=$rev-dirty-$(dirty_hash) dirty=1
    fi
  else
    # 다른 커밋은 임시 워크트리에서 빌드한다
    tmp=$PERF_DIR/build/$rev dir=$tmp
    if [ -d "$tmp" ]; then x git -C "$ROOT" worktree remove --force "$tmp"; fi
    x git -C "$ROOT" worktree add --detach "$tmp" "$sha"
  fi
  log "jar rev=$rev ($dir)"
  x "$dir/gradlew" -p "$dir" -q :api:bootJar -x test
  deps=$(x "$dir/gradlew" -p "$dir" -q :api:dependencies --configuration runtimeClasspath || true)
  hib=$(dep_ver hibernate-core "$deps") es=$(dep_ver elasticsearch-java "$deps")
  if [ "${SKIP_TESTS:-0}" = 1 ]; then te=skipped; else
    te=0; x "$dir/gradlew" -p "$dir" :api:test --tests '*ProductKeywordSearch*' >&2 || te=$?
  fi
  jar=$KIT/jars/api-$rev.jar env=$KIT/jars/api-$rev.env
  mkdir -p "$KIT/jars"
  x cp "$dir/boot/api/build/libs/api.jar" "$jar"
  local mb pid
  mb=$(git -C "$ROOT" merge-base "$sha" origin/develop 2>/dev/null || echo na)
  pid=$(git -C "$ROOT" diff "$mb" "$sha" 2>/dev/null | git -C "$ROOT" patch-id --stable | cut -d' ' -f1)
  {
    echo "git_rev=$sha"
    echo "git_tree=$(git -C "$ROOT" rev-parse "$sha^{tree}")"
    echo "merge_base=$mb"
    echo "patch_id=${pid:-na}"
    echo "dirty=$dirty"
    echo "jar=api-$rev.jar"
    if is_dry; then echo "jar_sha256=na"; else echo "jar_sha256=$(sha256_file "$jar")"; fi
    echo "hibernate_core=${hib:-na}"
    echo "elasticsearch_java=${es:-na}"
    echo "search_test_exit=$te"
  } > "$env.tmp"
  if is_dry; then cat "$env.tmp" >&2; rm -f "$env.tmp"; else mv "$env.tmp" "$env"; fi
  if [ -n "$tmp" ]; then x git -C "$ROOT" worktree remove --force "$tmp"; fi
  if [ "$LOCAL" != 1 ]; then
    need_outputs
    x aws s3 cp --only-show-errors "$jar" "s3://$BUCKET/jars/api-$rev.jar"
    x aws s3 cp --only-show-errors "$env" "s3://$BUCKET/jars/api-$rev.env"
  fi
  if [ "$te" != 0 ] && [ "$te" != skipped ]; then warn "검색 테스트 종료 코드 $te (env에 기록함)"; fi
  echo "REV=$rev"
}

cmd_sync() {
  need_outputs
  mkdir -p "$PERF_DIR"
  COPYFILE_DISABLE=1 tar czf "$PERF_DIR/kit.tgz" --no-xattrs \
    --exclude search/jars --exclude search/results/raw --exclude search/results/captures \
    --exclude search/tools/.venv --exclude search/.perf --exclude monitoring/.local \
    --exclude .DS_Store --exclude __pycache__ -C "$ROOT/loadtest" search monitoring
  x aws s3 cp --only-show-errors "$PERF_DIR/kit.tgz" "s3://$BUCKET/kit/current.tgz"
  # env.txt의 kit_hash로 그 run이 쓴 kit를 다시 받을 수 있게 해시 이름으로도 둔다
  x aws s3 cp --only-show-errors "$PERF_DIR/kit.tgz" "s3://$BUCKET/kit/$(kit_hash).tgz"
  log "kit 업로드"
}

cmd_up() {
  local ds=${DATASET:?DATASET 필요} db ids id st
  need_outputs
  db=$(db_id_of "$ds")
  cmd_sync
  ids="$RUNNER_ID $API_ID${ES_ID:+ $ES_ID}"
  x aws ec2 start-instances --instance-ids $ids >/dev/null
  # 이 데이터셋 RDS만 켜고 다른 perf RDS는 멈춘다
  rds_start "$db"
  perf_rds | while read -r id st; do
    if [ "$id" != "$db" ] && [ "$st" = available ]; then
      x aws rds stop-db-instance --db-instance-identifier "$id" >/dev/null
    fi
  done
  x aws ec2 wait instance-running --instance-ids $ids
  wait_ssm $ids
  x aws rds wait db-instance-available --db-instance-identifier "$db"
  ssm_run "$API_ID" "$INIT_WAIT/opt/perf/bin/kit-pull" 900
  if [ -n "$ES_ID" ]; then
    ssm_run "$ES_ID" "$INIT_WAIT/opt/perf/bin/kit-pull && docker compose -f /opt/perf/kit/search/es/compose.yml up -d --wait" 900
  fi
  ssm_run "$RUNNER_ID" "$INIT_WAIT/opt/perf/bin/kit-pull && bash /opt/perf/kit/monitoring/targets.sh --aws /opt/perf/state/outputs.json --dataset $(q "$ds") && docker compose -p aechak-perf-mon -f /opt/perf/kit/monitoring/compose.yml up -d --force-recreate" 1200
  log "up 완료: $ds ($db). make grafana로 화면을 연다"
}

cmd_down() {
  local ids id st
  ids=$(x aws ec2 describe-instances --output text --query 'Reservations[].Instances[].InstanceId' \
    --filters Name=tag:Project,Values=aechak-perf Name=instance-state-name,Values=pending,running)
  if [ -n "$ids" ]; then x aws ec2 stop-instances --instance-ids $ids >/dev/null; fi
  perf_rds | while read -r id st; do
    case $st in
      available) x aws rds stop-db-instance --db-instance-identifier "$id" >/dev/null ;;
      stopped|stopping|'') ;;
      *) warn "$id 상태 ${st}라 멈추지 않았다. 잠시 뒤 make down 다시" ;;
    esac
  done
  if is_dry; then
    x aws ec2 stop-instances --instance-ids '<Project=aechak-perf instances>'
    x aws rds stop-db-instance --db-instance-identifier '<Project=aechak-perf rds>'
  fi
  log "down 요청 완료"
}

cmd_init() {
  local ds=${DATASET:?DATASET 필요} rev db snap out
  rev=$(variant_rev "${REV:?REV 필요(기준 jar)}")
  if [ "$ds" = mirror ]; then die 1 "mirror는 스냅샷 복원으로 만든다"; fi
  need_jar "$rev"
  if [ "$LOCAL" = 1 ]; then
    out=$(x env PERF_ENV=local bash "$KIT/apihost/restart.sh" --rev "$rev" --dataset local --first-boot) || die "$E_API" "첫 부팅 실패"
    printf '%s\n' "$out"
    x bash "$KIT/lib/remote.sh" init local
    return 0
  fi
  need_outputs
  db=$(db_id_of "$ds") snap=aechak-perf-$ds-baseline
  if ! is_dry && aws rds describe-db-snapshots --db-snapshot-identifier "$snap" >/dev/null 2>&1; then
    die 1 "스냅샷 ${snap}이 이미 있다. 지운 뒤 다시"
  fi
  cmd_sync
  out=$(ssm_run "$API_ID" "/opt/perf/bin/kit-pull && bash $RUNNER_SCRIPTS/apihost/restart.sh $(q --rev "$rev" --dataset "$ds" --first-boot)" 900) \
    || die "$E_API" "첫 부팅 실패"
  printf '%s\n' "$out"
  on_runner "bash $RUNNER_SCRIPTS/lib/remote.sh init $(q "$ds")" 7200
  mkdir -p "$KIT/results/manifest"
  x aws s3 cp --only-show-errors "s3://$BUCKET/state/manifest-$ds.json" "$KIT/results/manifest/$ds.json"
  cmd_sync
  x aws rds create-db-snapshot --db-instance-identifier "$db" --db-snapshot-identifier "$snap" >/dev/null
  local i
  for i in 1 2 3 4; do
    if x aws rds wait db-snapshot-available --db-snapshot-identifier "$snap"; then break; fi
    [ "$i" -lt 4 ] || die 1 "스냅샷 대기 시간 초과: $snap"
  done
  log "init 완료: $ds, manifest results/manifest/$ds.json, 스냅샷 $snap"
}

cmd_seed() {
  local ds=${DATASET:?DATASET 필요}
  if [ "$LOCAL" = 1 ]; then x bash "$KIT/lib/remote.sh" seed local; return 0; fi
  need_outputs
  cmd_sync
  on_runner "bash $RUNNER_SCRIPTS/lib/remote.sh seed $(q "$ds")" 7200
}

cmd_manifest() {
  local ds=${DATASET:?DATASET 필요}
  if [ "$LOCAL" = 1 ]; then x bash "$KIT/lib/remote.sh" manifest local; return 0; fi
  need_outputs
  cmd_sync
  on_runner "bash $RUNNER_SCRIPTS/lib/remote.sh manifest $(q "$ds")" 3600
  mkdir -p "$KIT/results/manifest"
  x aws s3 cp --only-show-errors "s3://$BUCKET/state/manifest-$ds.json" "$KIT/results/manifest/$ds.json"
}

cmd_variant() {
  : "${STEP:?STEP 필요}" "${DATASET:?DATASET 필요}" "${VARIANT:?VARIANT 필요}" "${REV:?REV 필요}"
  local rev rc=0 log_file out ids k pre='' prog
  rev=$(variant_rev "$REV")
  need_jar "$rev"
  set -- --step "$STEP" --dataset "$DATASET" --variant "$VARIANT" --rev "$rev"
  if [ -n "${DDL:-}" ]; then set -- "$@" --ddl "$DDL"; fi
  if [ -n "${ENV:-}" ]; then set -- "$@" --env "$ENV"; fi
  if [ -n "${CELLS:-}" ]; then set -- "$@" --cells "$CELLS"; fi
  if [ -n "${BASE_RATE:-}" ]; then set -- "$@" --base-rate "$BASE_RATE" --base-source "${BASE_SOURCE:-manual}"; fi
  if [ "${FORCE:-0}" = 1 ]; then set -- "$@" --force; fi
  if [ "$LOCAL" = 1 ]; then
    [ "$DATASET" = local ] || die 1 "LOCAL=1이면 DATASET=local"
    if [ "${QUICK:-0}" = 1 ]; then set -- "$@" --quick; fi
    mkdir -p "$PERF_DIR/local/logs"
    log_file=$PERF_DIR/local/logs/$STEP-$VARIANT-$(date +%Y%m%d-%H%M%S).log
    x bash "$KIT/run.sh" "$@" --local | tee "$log_file" || rc=$?
    ids=$(sed -n 's/^RUN_ID //p' "$log_file" | grep -v -- '-warmup-' || true)
    VARIANT_IDS=$ids
    summarize "$PERF_DIR/local/raw" "$PERF_DIR/local/$STEP.md" $ids
    return "$rc"
  fi
  need_outputs
  cmd_sync
  mkdir -p "$PERF_DIR/logs"
  log_file=$PERF_DIR/logs/$STEP-$DATASET-$VARIANT-$(date +%Y%m%d-%H%M%S).log
  # SSM은 노트북 환경변수를 넘기지 않아 조절값을 명령줄에 붙인다
  for k in MAX_VUS_CAP IDLE_RDS_CPU_MAX RAMP_FLOOR_RATES RAMP_RATES WARMUP_RATE; do
    if [ -n "${!k:-}" ]; then pre="$pre$k=$(q "${!k}") "; fi
  done
  prog=s3://$BUCKET/progress/$(TZ=Asia/Seoul date +%F)-$STEP-$DATASET-$VARIANT.log
  if ! is_dry; then aws s3 rm --only-show-errors "$prog" >/dev/null 2>&1 || true; fi
  out=$(on_runner "${pre:+env $pre}bash $RUNNER_SCRIPTS/run.sh $(q "$@")" 21600 "$prog") || rc=$?
  printf '%s\n' "$out" | tee "$log_file"
  ids=$(printf '%s\n' "$out" | sed -n 's/^RUN_ID //p')
  VARIANT_IDS=$(printf '%s\n' $ids | grep -v -- '-warmup-' || true)
  for id in $ids; do
    # 다시 잰 run id는 노트북에 남은 이전 파일을 먼저 지운다
    if [ "${FORCE:-0}" = 1 ]; then rm -rf "${KIT:?}/results/raw/$id".* "$KIT/results/captures/$id"; fi
    fetch_run "$id"
  done
  summarize "$KIT/results/raw" "$KIT/results/$STEP.md" $VARIANT_IDS
  if [ "$rc" != 0 ]; then die "$rc" "run.sh 종료 코드 $rc (로그 $log_file)"; fi
}

# before를 먼저 돌리고 before ramp의 base_rate를 출처와 상관없이 after의 기준 요청률로 넘긴다. BASE_RATE를 주면 둘 다 그 값을 쓴다
# after 요약은 before run을 기준으로 읽어 spike 회복 기준을 같게 맞춘다
cmd_step() {
  : "${BEFORE_REV:?BEFORE_REV 필요}" "${AFTER_REV:?AFTER_REV 필요}"
  local raw=$KIT/results/raw rid r='' src='' STEP_REF=''
  if [ "$LOCAL" = 1 ]; then raw=$PERF_DIR/local/raw; fi
  VARIANT=before REV=$BEFORE_REV DDL=${BEFORE_DDL:-} cmd_variant
  STEP_REF=$VARIANT_IDS
  if [ -n "${BASE_RATE:-}" ]; then
    VARIANT=after REV=$AFTER_REV DDL=${AFTER_DDL:-} cmd_variant
    return 0
  fi
  rid=$(printf '%s\n' $VARIANT_IDS | grep -- '-ramp-' | head -n 1 || true)
  if [ -n "$rid" ]; then
    r=$(sed -n 's/^base_rate=//p' "$raw/$rid.env.txt" 2>/dev/null || true)
    src=$(sed -n 's/^base_rate_source=//p' "$raw/$rid.env.txt" 2>/dev/null || true)
  fi
  case $r in
    ''|na) warn "before ramp의 base_rate를 찾지 못해 after는 자기 ramp 결과로 돈다" ;;
    *)
      if awk -v b="$r" 'BEGIN{exit !(b > 0)}'; then
        log "after 기준 요청률 $r (before base_rate, 출처 $src)"
        if [ "$src" = capacity ]; then
          STEP_NOTE="before가 SLO를 만족한 단계가 없어(max_stable_rate 0) before의 처리 가능 요청률 $r req/s를 비교 부하로 after에 넘겼다."
        fi
        BASE_RATE=$r BASE_SOURCE=before VARIANT=after REV=$AFTER_REV DDL=${AFTER_DDL:-} cmd_variant
        return 0
      fi
      STEP_NOTE="before의 기준 요청률이 0이라(SLO도 처리 기준도 통과한 단계가 없다) 같은 부하 비교를 할 수 없다. after는 자기 ramp 결과로 돌렸다."
      warn "$STEP_NOTE" ;;
  esac
  VARIANT=after REV=$AFTER_REV DDL=${AFTER_DDL:-} cmd_variant
}

cmd_equiv() {
  : "${STEP:?STEP 필요}" "${DATASET:?DATASET 필요}" "${ENGINE:?ENGINE 필요(jpa|es)}" "${REV:?REV 필요}"
  local rev name
  rev=$(variant_rev "$REV") name=$STEP-$DATASET-$ENGINE${LABEL:+-$LABEL}
  need_jar "$rev"
  if [ "$LOCAL" = 1 ]; then
    x bash "$KIT/lib/remote.sh" equiv local "$STEP" "$ENGINE" "$rev" ${LABEL:+"$LABEL"}
    return 0
  fi
  need_outputs
  cmd_sync
  on_runner "bash $RUNNER_SCRIPTS/lib/remote.sh equiv $(q "$DATASET" "$STEP" "$ENGINE" "$rev") ${LABEL:+$(q "$LABEL")}" 3600
  mkdir -p "$KIT/results/equiv"
  x aws s3 cp --only-show-errors "s3://$BUCKET/results/equiv/$name.jsonl" "$KIT/results/equiv/$name.jsonl"
}

cmd_reindex() {
  : "${DATASET:?DATASET 필요}" "${REV:?REV 필요}"
  local rev
  rev=$(variant_rev "$REV")
  need_jar "$rev"
  need_outputs
  [ -n "$ES_ID" ] || die 1 "ES 호스트가 없다. terraform을 es_enabled=true로 apply하고 make outputs"
  cmd_sync
  on_runner "bash $RUNNER_SCRIPTS/lib/remote.sh reindex $(q "$DATASET" "$rev")" 9000
}

cmd_fetch() {
  local id
  : "${RUN:?RUN 필요(run id나 그 앞부분)}"
  need_outputs
  for id in $(x aws s3 ls "s3://$BUCKET/results/" | awk -v p="$RUN" '$1 == "PRE" { sub("/$", "", $2); if (index($2, p) == 1) print $2 }'); do
    log "fetch $id"
    fetch_run "$id"
  done
}

forward() {  # $1 원격 포트 $2 로컬 포트
  need_outputs
  x aws ssm start-session --target "$RUNNER_ID" --document-name AWS-StartPortForwardingSession \
    --parameters "{\"portNumber\":[\"$1\"],\"localPortNumber\":[\"$2\"]}"
}

case ${1:?target} in
  outputs) cmd_outputs ;;
  jar) cmd_jar ;;
  sync) cmd_sync ;;
  up) cmd_up ;;
  down) cmd_down ;;
  init) cmd_init ;;
  seed|reseed) cmd_seed ;;
  manifest) cmd_manifest ;;
  variant) cmd_variant ;;
  step) cmd_step ;;
  explain) VARIANT=${VARIANT:-explain} CELLS=explain cmd_variant ;;
  equiv) cmd_equiv ;;
  reindex) cmd_reindex ;;
  graphs) x "$KIT/tools/.venv/bin/python" -I "$KIT/tools/graphs.py" --raw "$KIT/results/raw" --out "$KIT/results/graphs" ;;
  grafana) forward 3000 13000 ;;
  prom) forward 9090 19090 ;;
  shell-runner) need_outputs; x aws ssm start-session --target "$RUNNER_ID" ;;
  shell-api) need_outputs; x aws ssm start-session --target "$API_ID" ;;
  fetch) cmd_fetch ;;
  *) die 1 "알 수 없는 타깃: $1" ;;
esac
