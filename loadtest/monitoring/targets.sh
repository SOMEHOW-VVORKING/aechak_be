#!/usr/bin/env bash
# 모니터링 compose가 읽을 수집 대상, mysqld_exporter 접속 정보, Grafana 관리자 비밀번호를 상태 디렉터리에 쓴다.
#   targets.sh --aws /opt/perf/state/outputs.json --dataset 100k|1m|mirror   (러너, 상태 디렉터리 /opt/perf/state)
#   targets.sh --local                                                     (노트북, 상태 디렉터리 loadtest/monitoring/.local)
# compose를 띄우기 전에 실행한다. 대상 파일은 Prometheus가 스스로 다시 읽으므로 데이터셋만 바꿀 때는
# 이 스크립트 뒤에 mysqld-exporter만 다시 만들면 된다(docker compose ... up -d --force-recreate mysqld-exporter).
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
MODE=""
OUTPUTS=""
DATASET=""

usage() {
  echo "usage: $0 --aws <outputs.json> --dataset 100k|1m|mirror | --local" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --aws) MODE=aws; OUTPUTS=${2:-}; shift 2 ;;
    --dataset) DATASET=${2:-}; shift 2 ;;
    --local) MODE=local; shift ;;
    *) usage ;;
  esac
done
[ -n "$MODE" ] || usage

# 비밀번호는 my.cnf와 compose env_file에 그대로 들어가므로 따옴표나 $ 같은 문자를 받지 않는다
check_secret() {
  case "$2" in
    ''|*[!A-Za-z0-9_.-]*) echo "ERROR: $1 must be non-empty and match [A-Za-z0-9_.-]" >&2; exit 1 ;;
  esac
}

# 대상 하나를 file SD JSON 한 항목으로 만든다. 인자: 주소, 라벨 JSON 본문
sd_entry() {
  printf '{"targets":["%s"],"labels":{%s}}' "$1" "$2"
}

# file SD 파일은 Prometheus가 감시하므로 임시 파일을 쓰고 옮긴다
write_sd() {
  local name=$1; shift
  local body
  body=$(IFS=,; echo "$*")
  printf '[%s]\n' "$body" > "$STATE/prom-targets/.$name.tmp"
  chmod 0644 "$STATE/prom-targets/.$name.tmp"
  mv "$STATE/prom-targets/.$name.tmp" "$STATE/prom-targets/$name.json"
}

# 비밀 파일을 0600으로 쓴다. 인자: 경로, 내용
write_secret() {
  ( umask 077; printf '%s\n' "$2" > "$1.tmp" )
  mv "$1.tmp" "$1"
}

write_mycnf() {
  # 인자: host, port, user, password. exporter 컨테이너는 nobody(65534)로 돈다
  write_secret "$STATE/mysqld-exporter.cnf" "$(printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s' "$1" "$2" "$3" "$4")"
  if [ "$(id -u)" = 0 ]; then
    chown 65534:65534 "$STATE/mysqld-exporter.cnf"
    chmod 0400 "$STATE/mysqld-exporter.cnf"
  fi
}

# Grafana와 렌더러가 함께 쓰는 토큰. 있으면 다시 써서 컨테이너가 불필요하게 재생성되지 않게 한다
write_renderer_token() {
  local token
  if [ -s "$STATE/renderer.env" ]; then
    token=$(sed -n 's/^AUTH_TOKEN=//p' "$STATE/renderer.env")
  fi
  [ -n "${token:-}" ] || token=$(openssl rand -hex 16)
  write_secret "$STATE/renderer.env" "$(printf 'AUTH_TOKEN=%s\nGF_RENDERING_RENDERER_TOKEN=%s' "$token" "$token")"
}

if [ "$MODE" = aws ]; then
  [ -f "$OUTPUTS" ] || { echo "ERROR: outputs file not found: $OUTPUTS" >&2; exit 1; }
  case "$DATASET" in 100k|1m|mirror) ;; *) usage ;; esac
  STATE=${PERF_STATE_DIR:-/opt/perf/state}

  REGION=$(jq -r '.region.value' "$OUTPUTS")
  API_IP=$(jq -r '.api.value.private_ip' "$OUTPUTS")
  ES_IP=$(jq -r '.es.value.private_ip // empty' "$OUTPUTS")
  if [ "$DATASET" = mirror ]; then
    DB=$(jq -c '.mirror.value // empty' "$OUTPUTS")
  else
    DB=$(jq -c --arg d "$DATASET" '.rds.value[$d] // empty' "$OUTPUTS")
  fi
  [ -n "$DB" ] || { echo "ERROR: no database for dataset $DATASET in $OUTPUTS" >&2; exit 1; }
  DBID=$(echo "$DB" | jq -r '.id')
  DB_ADDR=$(echo "$DB" | jq -r '.address')
  DB_PORT=$(echo "$DB" | jq -r '.port')

  ssm_get() {
    aws ssm get-parameter --region "$REGION" --with-decryption --name "$1" --query Parameter.Value --output text
  }
  EXPORTER_PW=$(ssm_get "$(jq -r '.exporter_password_param.value' "$OUTPUTS")")
  GRAFANA_PW=$(ssm_get "$(jq -r '.grafana_password_param.value' "$OUTPUTS")")
  check_secret exporter-password "$EXPORTER_PW"
  check_secret grafana-password "$GRAFANA_PW"

  mkdir -p "$STATE/prom-targets"
  chmod 0755 "$STATE/prom-targets"
  rm -f "$STATE"/prom-targets/*.json

  write_sd spring "$(sd_entry "$API_IP:8081" '"host":"api","application":"aechak-api"')"
  NODES="$(sd_entry "$API_IP:9100" '"host":"api"'),$(sd_entry node-exporter:9100 '"host":"runner"')"
  [ -z "$ES_IP" ] || NODES="$NODES,$(sd_entry "$ES_IP:9100" '"host":"es"')"
  write_sd node "$NODES"
  write_sd cadvisor "$(sd_entry "$API_IP:8088" '"host":"api"')"
  write_sd mysqld "$(sd_entry mysqld-exporter:9104 "\"dbid\":\"$DBID\",\"dataset\":\"$DATASET\"")"
  [ -z "$ES_IP" ] || write_sd es "$(sd_entry "$ES_IP:9114" '"host":"es"')"

  write_mycnf "$DB_ADDR" "$DB_PORT" exporter "$EXPORTER_PW"
  write_secret "$STATE/grafana-admin" "$GRAFANA_PW"
  write_secret "$STATE/grafana.env" "GF_SECURITY_ADMIN_PASSWORD=$GRAFANA_PW"
  write_renderer_token
  echo "TARGETS_READY mode=aws dataset=$DATASET dbid=$DBID api=$API_IP es=${ES_IP:-none} state=$STATE"
else
  STATE="$HERE/.local"
  mkdir -p "$STATE/prom-targets"
  rm -f "$STATE"/prom-targets/*.json

  write_sd spring "$(sd_entry host.docker.internal:18081 '"host":"api","application":"aechak-api"')"
  write_sd node "$(sd_entry node-exporter:9100 '"host":"runner"')"
  write_sd mysqld "$(sd_entry mysqld-exporter:9104 '"dbid":"local","dataset":"local"')"

  # make init LOCAL=1(lib/remote.sh users)이 만드는 exporter 계정. 그 전까지는 접속이 실패하고 만든 뒤 다음 수집부터 붙는다
  write_mycnf host.docker.internal 13306 "${LOCAL_MYSQL_USER:-exporter}" "${LOCAL_MYSQL_PASSWORD:-exporter}"
  # Grafana는 첫 기동 때의 비밀번호를 볼륨에 저장하므로 있으면 다시 쓴다
  if [ -s "$STATE/grafana-admin" ]; then
    GRAFANA_PW=$(cat "$STATE/grafana-admin")
  else
    GRAFANA_PW=$(openssl rand -hex 16)
  fi
  write_secret "$STATE/grafana-admin" "$GRAFANA_PW"
  write_secret "$STATE/grafana.env" "GF_SECURITY_ADMIN_PASSWORD=$GRAFANA_PW"
  write_renderer_token
  printf 'PERF_STATE_DIR=%s\nGRAFANA_BIND=127.0.0.1:13000\nPROM_BIND=127.0.0.1:19090\n' "$STATE" > "$STATE/compose.env"
  echo "TARGETS_READY mode=local state=$STATE env_file=$STATE/compose.env"
fi
