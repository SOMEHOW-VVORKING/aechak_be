#!/usr/bin/env bash
# api 컨테이너를 지정한 rev의 jar로 다시 만들고 health가 UP이 될 때까지 기다린다.
# AWS 모드는 api 호스트(/opt/perf/state/role이 api)에서, 노트북 모드는 PERF_ENV=local로 돈다.
# 성공하면 API_READY 한 줄을 찍고 0, 실패하면 api 로그 마지막 100줄을 stderr로 찍고 1로 끝난다.
# 노트북에서도 돌아서 bash 3.2 문법만 쓴다.
set -euo pipefail

usage() {
  echo "usage: $0 --rev REV --dataset 100k|1m|10m|100m|mirror|local [--first-boot] [--ready-timeout SEC] [--env KEY=VALUE]..." >&2
  exit 2
}

REV=""
DATASET=""
FIRST_BOOT=0
# 시작 직후 재색인처럼 준비 상태가 늦게 오는 실행은 --ready-timeout으로 늘린다
READY_TIMEOUT=240
EXTRA_ENV=()
while [ $# -gt 0 ]; do
  case "$1" in
    --rev) [ $# -ge 2 ] || usage; REV=$2; shift 2 ;;
    --dataset) [ $# -ge 2 ] || usage; DATASET=$2; shift 2 ;;
    --first-boot) FIRST_BOOT=1; shift ;;
    --ready-timeout) [ $# -ge 2 ] || usage; READY_TIMEOUT=$2; shift 2 ;;
    --env)
      [ $# -ge 2 ] || usage
      case "$2" in [A-Za-z_]*=*) EXTRA_ENV+=("$2") ;; *) usage ;; esac
      shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$REV" ] && [ -n "$DATASET" ] || usage

KIT_DIR=$(cd "$(dirname "$0")/.." && pwd)

now_ms() { python3 -I -c 'import time; print(int(time.time() * 1000))'; }
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}
# compose env_file는 작은따옴표 값을 그대로 읽는다(달러 기호 치환 없음)
kv() {
  case "$2" in *"'"* | *$'\n'*) echo "unsupported character in value of $1" >&2; exit 2 ;; esac
  printf "%s='%s'\n" "$1" "$2"
}

if [ "${PERF_ENV:-}" = local ]; then
  MODE=local
elif [ "$(cat /opt/perf/state/role 2>/dev/null || true)" = api ]; then
  MODE=aws
else
  echo "not an api host; set PERF_ENV=local on the laptop" >&2
  exit 2
fi

if [ "$MODE" = aws ]; then
  # SSM RunShellScript는 /etc/profile.d를 읽지 않는다
  export AWS_REGION=${AWS_REGION:-ap-northeast-2} AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-ap-northeast-2}
  STATE=/opt/perf/state
  OUTPUTS=$STATE/outputs.json
  BUCKET=$(cat "$STATE/bucket")
  JAR=/opt/perf/jars/api-$REV.jar
  META=/opt/perf/jars/api-$REV.env
  mkdir -p /opt/perf/jars
  for f in "$JAR" "$META"; do
    if [ ! -s "$f" ]; then
      aws s3 cp "s3://$BUCKET/jars/$(basename "$f")" "$f.tmp" --only-show-errors
      mv "$f.tmp" "$f"
    fi
  done
  case "$DATASET" in
    mirror) DB_HOST=$(jq -er '.mirror.value.address' "$OUTPUTS"); DB_PORT=$(jq -er '.mirror.value.port' "$OUTPUTS") ;;
    100k | 1m | 10m | 100m)
      DB_HOST=$(jq -er --arg d "$DATASET" '.rds.value[$d].address' "$OUTPUTS")
      DB_PORT=$(jq -er --arg d "$DATASET" '.rds.value[$d].port' "$OUTPUTS") ;;
    *) usage ;;
  esac
  DB_USER=$(jq -er '.db_username.value' "$OUTPUTS")
  DB_PASS=$(aws ssm get-parameter --name "$(jq -er '.db_password_param.value' "$OUTPUTS")" \
    --with-decryption --query Parameter.Value --output text)
  ENV_FILE=$STATE/api.env
  ES_IP=$(jq -r '.es.value.private_ip // empty' "$OUTPUTS")
  HEALTH_URL=http://localhost:8081/actuator/health
  COMPOSE=(docker compose -p aechak-perf-api -f "$KIT_DIR/apihost/compose.yml")
  SUPPORT_SERVICES=(redis kafka node-exporter cadvisor)
else
  JAR=$KIT_DIR/jars/api-$REV.jar
  META=$KIT_DIR/jars/api-$REV.env
  DB_HOST=mysql
  DB_PORT=3306
  DB_USER=root
  DB_PASS=local
  mkdir -p "$KIT_DIR/.perf/local"
  ENV_FILE=$KIT_DIR/.perf/local/api.env
  HEALTH_URL=http://localhost:18081/actuator/health
  COMPOSE=(docker compose -p aechak-perf -f "$KIT_DIR/compose.yml")
  SUPPORT_SERVICES=(mysql redis kafka)
fi

[ -s "$JAR" ] || { echo "jar not found: $JAR" >&2; exit 2; }
# 노트북에서 손으로 넣은 jar는 메타 파일이 없을 수 있어 그때만 검증을 건너뛴다
if [ -s "$META" ]; then
  want=$(sed -n 's/^jar_sha256=//p' "$META")
  got=$(sha256_of "$JAR")
  [ "$want" = "$got" ] || { echo "jar_sha256 mismatch for $JAR: want=$want got=$got" >&2; exit 2; }
elif [ "$MODE" = aws ]; then
  echo "missing $META" >&2
  exit 2
fi

if [ "$FIRST_BOOT" = 1 ]; then
  FLYWAY=true
  DDL_AUTO=update
else
  FLYWAY=false
  DDL_AUTO=none
fi

umask 077
{
  kv TZ Asia/Seoul
  kv SPRING_PROFILES_ACTIVE local
  kv SPRING_DATASOURCE_URL "jdbc:mysql://$DB_HOST:$DB_PORT/aechak"
  kv SPRING_DATASOURCE_USERNAME "$DB_USER"
  kv SPRING_DATASOURCE_PASSWORD "$DB_PASS"
  kv SPRING_DATA_REDIS_HOST redis
  kv KAFKA_BOOTSTRAP_SERVERS kafka:9092
  # 검색은 S3를 부르지 않지만 local 프로필이 버킷 이름을 필수로 요구한다
  kv AWS_S3_MEDIA_BUCKET perf-dummy
  kv AWS_S3_DOCS_BUCKET perf-dummy
  kv MANAGEMENT_SERVER_PORT 8081
  kv MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE health,prometheus
  kv MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILESHISTOGRAM_HTTP_SERVER_REQUESTS true
  kv SPRING_FLYWAY_ENABLED "$FLYWAY"
  kv SPRING_JPA_HIBERNATE_DDL_AUTO "$DDL_AUTO"
  # ES 호스트가 있을 때만 검색 엔진 주소를 준다. 엔진 선택은 --env AECHAK_SEARCH_ENGINE으로 한다
  if [ -n "${ES_IP:-}" ]; then kv SPRING_ELASTICSEARCH_URIS "http://$ES_IP:9200"; fi
} >"$ENV_FILE.tmp"
# --env는 같은 키의 기본값을 대신한다
for pair in ${EXTRA_ENV[@]+"${EXTRA_ENV[@]}"}; do
  key=${pair%%=*}
  grep -v "^$key=" "$ENV_FILE.tmp" >"$ENV_FILE.tmp2" || true
  kv "$key" "${pair#*=}" >>"$ENV_FILE.tmp2"
  mv "$ENV_FILE.tmp2" "$ENV_FILE.tmp"
done
mv "$ENV_FILE.tmp" "$ENV_FILE"

export API_JAR=$JAR API_ENV_FILE=$ENV_FILE API_LOG_STREAM="api-$REV-$(date +%s)"

fail() {
  echo "API_NOT_READY rev=$REV dataset=$DATASET reason=$1" >&2
  "${COMPOSE[@]}" logs --no-color --tail 100 api >&2 || true
  exit 1
}

"${COMPOSE[@]}" up -d --wait "${SUPPORT_SERVICES[@]}"
STARTED_MS=$(now_ms)
"${COMPOSE[@]}" up -d --force-recreate --no-deps api
CID=$("${COMPOSE[@]}" ps -q api)
DEADLINE=$(($(date +%s) + READY_TIMEOUT))
until curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; do
  [ "$(docker inspect -f '{{.State.Running}}' "$CID" 2>/dev/null)" = true ] || fail exited
  [ "$(date +%s)" -lt "$DEADLINE" ] || fail timeout
  sleep 2
done
echo "API_READY rev=$REV dataset=$DATASET started_ms=$STARTED_MS"
