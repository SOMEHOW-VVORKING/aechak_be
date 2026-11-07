#!/usr/bin/env bash
# ES 호스트에서 측정 중 교란을 건다. run.sh가 lib/perturb.sh를 거쳐 SSM으로 부른다
#   prepare MODE                      교란 전 확인과 남은 교란 정리. delay면 tc가 없을 때 iproute-tc를 설치한다
#   run MODE START_MS END_MS [VALUE]  START_MS부터 END_MS까지 교란을 걸고 PERTURB_START, PERTURB_END 줄을 찍는다
#   clear                             남은 지연과 정지를 푼다
# MODE: write(VALUE 초당 문서 수, 같은 _source를 다시 색인), delay(VALUE ms, ES 응답 패킷 지연), pause(ES 컨테이너 정지)
# 교란은 END_MS에 스스로 풀리고 스크립트가 중간에 끝나도 EXIT trap이 푼다
set -euo pipefail

# 두 값은 노트북에서 스크립트를 확인할 때만 바꾼다
ES=${PERTURB_ES:-http://localhost:9200}
CONTAINER=${PERTURB_CONTAINER:-perf-es}
ES_PORT=9200
HERE=$(cd "$(dirname "$0")" && pwd)

now_ms() { python3 -I -c 'import time; print(int(time.time() * 1000))'; }
# 백그라운드로 재우고 wait해서 TERM이 오면 바로 trap이 돈다
sleep_until() {
  python3 -I -c 'import sys, time; time.sleep(max(0.0, int(sys.argv[1]) / 1000 - time.time()))' "$1" &
  SLEEP_PID=$!
  wait "$SLEEP_PID"
}
net_dev() { ip route show default | awk '{print $5; exit}'; }

# 루트 qdisc에 netem이 있을 때만 지운다. 지우면 커널 기본 qdisc로 돌아간다
clear_delay() {
  local d
  command -v tc >/dev/null 2>&1 || return 0
  d=$(net_dev)
  if tc qdisc show dev "$d" | grep -q netem; then tc qdisc del dev "$d" root; echo "delay 해제 $d" >&2; fi
}
clear_pause() {
  if [ "$(docker inspect -f '{{.State.Paused}}' "$CONTAINER" 2>/dev/null || true)" = true ]; then
    docker unpause "$CONTAINER" >/dev/null
    echo "pause 해제 $CONTAINER" >&2
  fi
}
clear_all() {
  if [ -n "${SLEEP_PID:-}" ]; then kill "$SLEEP_PID" 2>/dev/null || true; fi
  clear_delay || true
  clear_pause || true
}

# 4번째 band에는 필터로 고른 ES 응답만 들어간다. 기본 priomap은 band 0에서 2만 쓴다
apply_delay() {
  local d; d=$(net_dev)
  tc qdisc replace dev "$d" root handle 1: prio bands 4 priomap 1 2 2 2 1 2 0 0 1 1 1 1 1 1 1 1
  tc qdisc add dev "$d" parent 1:4 handle 40: netem delay "${1}ms" limit 100000
  tc filter add dev "$d" protocol ip parent 1: prio 4 u32 match ip sport "$ES_PORT" 0xffff flowid 1:4
}

cmd=${1:?prepare|run|clear}
case $cmd in
  clear) clear_all ;;
  prepare)
    mode=${2:?mode}
    clear_all
    [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || true)" = true ] || { echo "$CONTAINER 컨테이너가 돌고 있지 않다" >&2; exit 1; }
    case $mode in
      delay)
        if ! command -v tc >/dev/null 2>&1; then dnf install -y -q iproute-tc >&2; fi
        tc -V >&2 ;;
      write) curl -fsS --max-time 5 "$ES/products/_count" >&2; echo >&2 ;;
      pause) ;;
      *) echo "알 수 없는 MODE: $mode" >&2; exit 2 ;;
    esac
    echo "PERTURB_READY $mode" ;;
  run)
    mode=${2:?mode} start=${3:?start_ms} end=${4:?end_ms} value=${5:-}
    case "$start/$end" in *[!0-9/]*) echo "START_MS와 END_MS는 epoch ms" >&2; exit 2 ;; esac
    trap clear_all EXIT
    trap 'exit 143' TERM INT HUP
    case $mode in
      write) python3 -I "$HERE/rewrite.py" --es "$ES" --start-ms "$start" --end-ms "$end" --rate "${value:?초당 문서 수}" ;;
      delay)
        case ${value:?ms} in ''|*[!0-9]*) echo "delay는 정수 ms" >&2; exit 2 ;; esac
        sleep_until "$start"
        apply_delay "$value"
        echo "PERTURB_START $(now_ms)"
        sleep_until "$end"
        clear_delay
        echo "PERTURB_END $(now_ms)" ;;
      pause)
        sleep_until "$start"
        docker pause "$CONTAINER" >/dev/null
        echo "PERTURB_START $(now_ms)"
        sleep_until "$end"
        clear_pause
        echo "PERTURB_END $(now_ms)" ;;
      *) echo "알 수 없는 MODE: $mode" >&2; exit 2 ;;
    esac ;;
  *) echo "usage: $0 prepare MODE | run MODE START_MS END_MS [VALUE] | clear" >&2; exit 2 ;;
esac
