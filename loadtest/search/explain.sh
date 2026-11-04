#!/usr/bin/env bash
# 고정 요청마다 api가 실행한 SELECT를 events_statements_history_long에서 꺼내 EXPLAIN ANALYZE를 두 번 돌리고 두 번째 결과를 남긴다
# DB 접속 정보는 run.sh가 넘긴 환경변수를 쓰고, 없으면 DATASET(또는 --local)으로 찾는다
set -euo pipefail
# shellcheck source=lib/common.sh
. "$(cd "$(dirname "$0")" && pwd)/lib/common.sh"

usage() { echo 'usage: explain.sh --out FILE [--local]' >&2; exit 1; }
OUTF='' LOCAL=0
while [ $# -gt 0 ]; do
  case $1 in
    --out) OUTF=${2:?}; shift 2 ;;
    --local) LOCAL=1; shift ;;
    *) usage ;;
  esac
done
[ -n "$OUTF" ] || usage
if [ -z "${DB_HOST:-}" ]; then
  if [ "$LOCAL" = 1 ]; then resolve_env local; else resolve_env "${DATASET:?DATASET 환경변수 필요}"; fi
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
echo "UPDATE performance_schema.setup_consumers SET enabled = 'YES' WHERE name = 'events_statements_history_long'" | sql \
  || warn "events_statements_history_long consumer를 켜지 못했다"

# call_api <kw> <sort> [cursor]: 응답 본문은 $TMP/body, 상태 코드를 찍는다
call_api() {
  local c=(curl -s -G --max-time 60 -o "$TMP/body" -w '%{http_code}' "$BASE_URL/search/products"
           --data-urlencode "keyword=$1" --data-urlencode "sort=$2")
  if [ -n "${3:-}" ]; then c+=(--data-urlencode "cursor=$3"); fi
  "${c[@]}" || true
}

explain_one() {  # $1 kw $2 sort $3 page $4 cursor
  local code total n=0 t e text kind res
  echo 'TRUNCATE performance_schema.events_statements_history_long' | sql
  code=$(call_api "$1" "$2" "${4:-}")
  total=$(jq -r '.data.totalCount // "null"' "$TMP/body" 2>/dev/null || echo na)
  sql > "$TMP/ids" <<'SQL'
SELECT h.thread_id, h.event_id
FROM performance_schema.events_statements_history_long h
JOIN performance_schema.threads t ON t.thread_id = h.thread_id
WHERE t.processlist_user IS NOT NULL AND t.processlist_user NOT IN ('exporter', 'rdsadmin')
  AND h.thread_id <> PS_CURRENT_THREAD_ID() AND h.event_name = 'statement/sql/select'
  AND h.current_schema = 'aechak' AND h.sql_text NOT LIKE '%performance_schema%' AND h.sql_text NOT LIKE 'select @@%'
  AND h.sql_text LIKE '%from products %'
ORDER BY h.event_id
SQL
  # 이벤트를 먼저 다 읽고 나서 EXPLAIN을 돌린다
  while read -r t e; do
    if [ -z "$t" ]; then continue; fi
    n=$((n + 1))
    echo "SELECT sql_text FROM performance_schema.events_statements_history_long WHERE thread_id = $t AND event_id = $e" \
      | sql -r > "$TMP/sql.$n"
  done < "$TMP/ids"
  echo "===== kw=$1 sort=$2 page=$3 status=$code totalCount=$total statements=$n ====="
  local k=1
  while [ "$k" -le "$n" ]; do
    text=$(cat "$TMP/sql.$k")
    if grep -qi 'count(' "$TMP/sql.$k"; then kind=count; else kind=list; fi
    echo "--- [$k] $kind ---"
    printf '%s\n' "$text"
    if [ "${#text}" -ge 4000 ]; then echo "(sql_text가 잘렸을 수 있다. performance_schema_max_sql_text_length 확인)"; fi
    echo "--- EXPLAIN ANALYZE (두 번째 실행) ---"
    printf 'EXPLAIN ANALYZE %s;\n' "$text" | sql -r >/dev/null 2>&1 || true
    res=$(printf 'EXPLAIN ANALYZE %s;\n' "$text" | sql -r 2>&1) || res="EXPLAIN 실패: $res"
    printf '%s\n\n' "$res"
    k=$((k + 1))
  done
}

{
  for kw in 강아지 사료 관절 한정판 없는상품; do
    explain_one "$kw" popular 1
    cursor=$(jq -r '.data.nextCursor // empty' "$TMP/body" 2>/dev/null || true)
    explain_one "$kw" latest 1
    explain_one "$kw" price_asc 1
    if [ -n "$cursor" ]; then
      explain_one "$kw" popular 2 "$cursor"
    else
      echo "===== kw=$kw sort=popular page=2 다음 페이지 없음 ====="
    fi
  done
} > "$OUTF"
log "explain 완료: $OUTF"
