#!/usr/bin/env bash
# 러너(또는 노트북 local)에서 DB 준비 작업을 한다. lib/laptop.sh가 SSM으로 부르거나 LOCAL=1이면 직접 부른다
#   users D | seed D | manifest D [seeded_at] | init D | equiv D STEP ENGINE REV [LABEL] | reindex D REV
# shellcheck disable=SC2119
set -euo pipefail
# shellcheck source=lib/common.sh
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

action=${1:?action}
DS=${2:?dataset}
resolve_env "$DS"
mkdir -p "$OUT/manifest" "$OUT/equiv"
if [ "$LOCAL" = 1 ]; then MF=$OUT/manifest/local.json; else MF=$KIT/results/manifest/$DS.json; fi

do_users() {
  local pw
  if [ "$LOCAL" = 1 ]; then pw=${LOCAL_EXPORTER_PASS:-exporter}; else pw=$(ssm_param exporter_password_param); fi
  { printf "SET @exporter_password := '%s';\n" "$(printf '%s' "$pw" | sed "s/'/''/g")"; cat "$KIT/sql/init-users.sql"; } | sql
}

do_seed() {
  local n
  case $DS in
    100k) n=100000 ;;
    1m) n=1000000 ;;
    10m) n=10000000 ;;
    local) n=${LOCAL_N_PRODUCTS:-100000} ;;
    *) die 1 "${DS}는 시드하지 않는다(스냅샷 복원)" ;;
  esac
  log "seed $DS (${n}건)"
  { echo "SET @n_products := $n;"; cat "$KIT/seed.sql"; } | sql
  echo 'ANALYZE TABLE products, product_stats, sellers, categories' | sql >/dev/null
  sql < "$KIT/sql/warm.sql" >/dev/null
  log "seed 완료"
}

# manifest.sql 결과와 인덱스 목록, 테이블 크기를 JSON으로 묶는다
do_manifest() {
  local at=${1:-} kv idx ts out=$OUT/manifest/$DS.json
  if [ -z "$at" ]; then at=$(jq -r '.seeded_at // "na"' "$MF" 2>/dev/null || echo na); fi
  kv=$(sql < "$KIT/sql/manifest.sql")
  idx=$(echo "SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index), index_type, non_unique FROM information_schema.statistics WHERE table_schema = 'aechak' AND table_name IN ('products', 'product_stats', 'sellers', 'categories') GROUP BY table_name, index_name, index_type, non_unique ORDER BY 1, 2" | sql)
  ts=$(echo "SELECT table_name, table_rows, data_length, index_length FROM information_schema.tables WHERE table_schema = 'aechak' AND table_name IN ('products', 'product_stats', 'sellers', 'categories') ORDER BY 1" | sql)
  printf '%s\n' "$kv" | jq -R -s --arg ds "$DS" --arg at "$at" --arg idx "$idx" --arg ts "$ts" '
    def num: (tonumber? // .);
    def tsv: split("\n") | map(select(length > 0) | split("\t"));
    def pick(p): map(select(.[0] | startswith(p)) | {key: (.[0] | ltrimstr(p)), value: (.[1] | num)}) | from_entries;
    def one(k): (map(select(.[0] == k) | .[1] | num) | first) // null;
    tsv as $r
    | {dataset: $ds, seeded_at: $at, rows: ($r | pick("rows.")), crc: ($r | pick("crc.")),
       active_discounts: ($r | one("active_discounts")), visible_products: ($r | one("visible_products")),
       keywords: ($r | pick("keyword.")),
       indexes: ($idx | tsv | map({table: .[0], name: .[1], columns: .[2], type: .[3], non_unique: (.[4] | num)})),
       table_status: ($ts | tsv | map({table: .[0], rows: (.[1] | num), data_length: (.[2] | num), index_length: (.[3] | num)}))}' \
    > "$out.tmp"
  mv "$out.tmp" "$out"
  if [ -n "$BUCKET" ]; then x aws s3 cp --only-show-errors "$out" "s3://$BUCKET/state/manifest-$DS.json"; fi
  log "manifest: $out"
}

do_equiv() {  # STEP ENGINE REV [LABEL]
  local step=${1:?step} engine=${2:?engine} rev=${3:?rev} name out
  name=$step-$DS-$engine${4:+-$4}
  out=$OUT/equiv/$name.jsonl
  restart_api --rev "$rev" --dataset "$DS" --env "AECHAK_SEARCH_ENGINE=$engine" || die "$E_API" "api가 준비되지 않았다"
  x "$K6_BIN" run --quiet --log-format raw -e "BASE_URL=$BASE_URL" "$KIT/equiv.js" 2>&1 \
    | sed -n 's/^EQUIV //p' > "$out" || die "$E_K6" "equiv.js 실패"
  log "equiv: $out ($(wc -l < "$out" | tr -d ' ')줄)"
  if [ -n "$BUCKET" ]; then x aws s3 cp --only-show-errors "$out" "s3://$BUCKET/results/equiv/$name.jsonl"; fi
}

# 엔진을 es로 둔 api를 재색인 속성과 함께 다시 띄우고 색인 문서 수가 상품 수와 같아질 때까지 기다린다
# 재색인은 시작 직후 실행돼 끝나야 api가 준비 상태가 되므로 준비 대기를 2시간까지 늘린다
do_reindex() {  # REV
  # 색인 이름은 앱의 상품 검색 색인과 같다
  local rev=${1:?rev} index=products want got='' t0 i size
  [ -n "$ES_URL" ] || die 1 "ES 호스트가 없다. terraform을 es_enabled=true로 apply하고 make outputs"
  want=$(echo 'SELECT COUNT(*) FROM products' | sql)
  # 이전 색인이 남아 있으면 문서 수가 처음부터 같아 보여서 먼저 지운다
  curl -sS -o /dev/null -X DELETE "$ES_URL/$index" || true
  t0=$(now_ms)
  restart_api --rev "$rev" --dataset "$DS" --ready-timeout 7200 --env AECHAK_SEARCH_ENGINE=es --env AECHAK_SEARCH_ES_REINDEX_ON_STARTUP=true \
    || die "$E_API" "api가 준비되지 않았다"
  for i in $(seq 1 720); do
    got=$(curl -fsS --max-time 5 "$ES_URL/$index/_count" 2>/dev/null | jq -r '.count // empty' || true)
    if [ "$got" = "$want" ]; then break; fi
    sleep 5
  done
  [ "$got" = "$want" ] || die 1 "재색인이 끝나지 않았다(색인 ${got:-0}건, 상품 ${want}건)"
  size=$(curl -fsS "$ES_URL/_cat/indices/$index?h=store.size&bytes=mb" | tr -d ' ')
  log "REINDEX_DONE dataset=$DS docs=$want seconds=$(( ($(now_ms) - t0) / 1000 )) store_mb=$size (api 재시작 포함)"
}

case $action in
  users) do_users ;;
  seed|reseed) do_seed ;;
  manifest) do_manifest "${3:-}" ;;
  init)
    do_users
    do_seed
    do_manifest "$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z')" ;;
  equiv) shift 2; do_equiv "$@" ;;
  reindex) shift 2; do_reindex "$@" ;;
  *) die 1 "알 수 없는 action: $action" ;;
esac
