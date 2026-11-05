#!/usr/bin/env bash
# run 시간 범위로 Grafana 대시보드(aechak-search-perf) 패널을 PNG로 저장한다.
#   capture.sh --run-id ID --from MS --to MS [--dbid aechak-perf-100k] [--panels "1 2 ..."] [--out DIR] [--grafana URL] [--no-dashboard]
# 패널마다 NN-slug.png(1200x500)와 전체 화면 00-dashboard.png를 쓴다. 범위는 앞뒤로 30초씩 넓힌다.
# 렌더는 CAPTURE_PARALLEL(기본 3)개씩 동시에 요청한다. 일부 패널이 실패해도 0으로 끝나고 실패한 패널을 출력한다.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
DASH_UID=aechak-search-perf
RUN_ID=""
FROM=""
TO=""
DBID=aechak-perf-100k
PANELS="1 2 3 4 5 6 7 8 9 10 11 12 13 14 17 18 20"
OUT=""
GRAFANA=""
DASHBOARD=1
PARALLEL=${CAPTURE_PARALLEL:-3}

usage() {
  echo "usage: $0 --run-id ID --from MS --to MS [--dbid ID] [--panels \"1 2 ...\"] [--out DIR] [--grafana URL] [--no-dashboard]" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --run-id) RUN_ID=${2:-}; shift 2 ;;
    --from) FROM=${2:-}; shift 2 ;;
    --to) TO=${2:-}; shift 2 ;;
    --dbid) DBID=${2:-}; shift 2 ;;
    --panels) PANELS=${2:-}; shift 2 ;;
    --out) OUT=${2:-}; shift 2 ;;
    --grafana) GRAFANA=${2:-}; shift 2 ;;
    --no-dashboard) DASHBOARD=0; shift ;;
    *) usage ;;
  esac
done
[ -n "$RUN_ID" ] && [ -n "$FROM" ] && [ -n "$TO" ] || usage
case "$FROM$TO" in *[!0-9]*) usage ;; esac

# 러너에는 /opt/perf/state/role이 있다. 없거나 PERF_ENV=local이면 노트북으로 본다
if [ "${PERF_ENV:-}" != local ] && [ -f /opt/perf/state/role ]; then
  PW_FILE=/opt/perf/state/grafana-admin
  : "${GRAFANA:=http://localhost:3000}"
  : "${OUT:=/opt/perf/results/captures/$RUN_ID}"
else
  PW_FILE="$HERE/../monitoring/.local/grafana-admin"
  : "${GRAFANA:=http://localhost:13000}"
  : "${OUT:=$HERE/.perf/local/captures/$RUN_ID}"
fi
[ -s "$PW_FILE" ] || { echo "ERROR: grafana password file missing: $PW_FILE" >&2; exit 1; }
PW=$(cat "$PW_FILE")
mkdir -p "$OUT"

slug() {
  case "$1" in
    1) echo rds-cpu ;; 2) echo rds-connections ;; 3) echo mysql-threads ;; 4) echo mysql-qps ;;
    5) echo innodb-disk-reads ;; 6) echo api-cpu ;; 7) echo api-memory-heap ;; 8) echo hikari-pool ;;
    9) echo hikari-acquire-timeout ;; 10) echo http-server-latency ;; 11) echo k6-rate ;; 12) echo k6-latency ;;
    13) echo k6-errors ;; 14) echo runner-cpu ;; 15) echo es-cpu-heap ;; 16) echo es-search-latency ;;
    17) echo api-logs ;; 18) echo slow-log ;; 19) echo rds-credits ;; 20) echo rds-iops-memory ;;
    *) return 1 ;;
  esac
}

FROM_PAD=$((FROM - 30000))
TO_PAD=$((TO + 30000))
QS="orgId=1&from=$FROM_PAD&to=$TO_PAD&tz=Asia%2FSeoul&timeout=90&var-dbid=$DBID&var-testid=$RUN_ID"

# 렌더 하나를 받아 PNG인지 확인한다. 인자: URL 경로와 쿼리, 저장 파일
render() {
  local tmp="$2.tmp" code ctype
  # 비밀번호가 프로세스 목록에 보이지 않게 curl 설정을 표준 입력으로 넘긴다
  if ! read -r code ctype < <(printf 'user = "admin:%s"\n' "$PW" | curl -sS -K - --max-time 150 \
      -o "$tmp" -w '%{http_code} %{content_type}\n' "$GRAFANA$1"); then
    rm -f "$tmp"; return 1
  fi
  if [ "$code" = 200 ] && [ "${ctype%%;*}" = image/png ]; then
    mv "$tmp" "$2"
  else
    echo "  $(basename "$2"): http=$code type=$ctype $(head -c 200 "$tmp" 2>/dev/null | tr -d '\n')" >&2
    rm -f "$tmp"; return 1
  fi
}

# 렌더 목록: 이름, URL, 저장 파일
JOBS=""
for id in $PANELS; do
  if ! s=$(slug "$id"); then JOBS="$JOBS$id - -"$'\n'; continue; fi
  JOBS="$JOBS$id /render/d-solo/$DASH_UID/x?$QS&panelId=$id&width=1200&height=500 $(printf '%s/%02d-%s.png' "$OUT" "$id" "$s")"$'\n'
done
if [ "$DASHBOARD" = 1 ]; then JOBS="${JOBS}dashboard /render/d/$DASH_UID/x?$QS&width=1600&height=-1&kiosk=true $OUT/00-dashboard.png"$'\n'; fi

# PARALLEL개씩 백그라운드로 돌리고 파일이 생겼는지로 성공을 센다
OK=0
FAILED=""
n=0
while read -r name url f; do
  [ -n "$name" ] || continue
  if [ "$f" != - ]; then rm -f "$f"; render "$url" "$f" & fi
  n=$((n + 1))
  if [ $((n % PARALLEL)) = 0 ]; then wait || true; fi
done <<<"$JOBS"
wait || true
while read -r name url f; do
  [ -n "$name" ] || continue
  if [ "$f" != - ] && [ -s "$f" ]; then OK=$((OK + 1)); else FAILED="$FAILED $name"; fi
done <<<"$JOBS"

FAILED=$(echo $FAILED | tr " " ",")
echo "CAPTURE_DONE run_id=$RUN_ID ok=$OK failed=${FAILED:-none} out=$OUT"
exit 0
