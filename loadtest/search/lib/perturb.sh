# shellcheck shell=bash
# steady run에 ES 교란을 건다. run.sh가 source하고 ES 호스트 쪽 동작은 es/perturb.sh에 있다
#   PERTURB=write  초당 PERTURB_RATE(기본 10)건 같은 _source 다시 색인. 기본 구간은 run 전체
#   PERTURB=delay  ES 응답에 PERTURB_DELAY_MS(기본 1000) 지연. 기본 구간은 60초부터 60초 동안
#   PERTURB=pause  ES 컨테이너 docker pause. 기본 구간은 60초부터 30초 동안
# 구간은 k6 시작부터 PERTURB_AT_S초 뒤에서 PERTURB_FOR_S초 동안이다. PERTURB가 비어 있으면 아무것도 하지 않는다
# shellcheck disable=SC2034  # ENV_ 값은 write_env가 간접 참조로 읽는다

PERTURB=${PERTURB:-}
PERTURB_PID='' PERTURB_OUT='' ES_ID=''
ES_KIT=/opt/perf/kit/search/es/perturb.sh

# 변형 시작에서 한 번: 값을 확인하고 ES 호스트에 kit를 받아 교란을 준비한다
perturb_init() {
  case $PERTURB in
    '') return 0 ;;
    write) PERTURB_VALUE=${PERTURB_RATE:-10} PERTURB_AT_S=${PERTURB_AT_S:-0} PERTURB_FOR_S=${PERTURB_FOR_S:-} ;;
    delay) PERTURB_VALUE=${PERTURB_DELAY_MS:-1000} PERTURB_AT_S=${PERTURB_AT_S:-60} PERTURB_FOR_S=${PERTURB_FOR_S:-60} ;;
    pause) PERTURB_VALUE='' PERTURB_AT_S=${PERTURB_AT_S:-60} PERTURB_FOR_S=${PERTURB_FOR_S:-30} ;;
    *) die 1 "알 수 없는 PERTURB: $PERTURB (write, delay, pause)" ;;
  esac
  case $PERTURB_AT_S in ''|*[!0-9]*) die 1 "PERTURB_AT_S는 정수 초" ;; esac
  case $PERTURB_FOR_S in *[!0-9]*) die 1 "PERTURB_FOR_S는 정수 초" ;; esac
  case $PERTURB/$PERTURB_VALUE in delay/|delay/*[!0-9]*) die 1 "PERTURB_DELAY_MS는 정수 ms" ;; esac
  if [ "$PERTURB" = write ] && ! awk -v r="$PERTURB_VALUE" 'BEGIN{exit !(r + 0 > 0 && r ~ /^[0-9.]+$/)}'; then die 1 "PERTURB_RATE는 양수"; fi
  [ "$LOCAL" = 0 ] || die 1 "PERTURB는 ES 호스트가 있는 AWS 측정에서만 쓴다"
  ES_ID=$(jq -r '.es.value.instance_id // empty' "$OUTPUTS")
  [ -n "$ES_ID" ] || die 1 "PERTURB를 쓰려면 ES 호스트가 있어야 한다"
  log "ES 교란 준비 $PERTURB"
  ssm_run "$ES_ID" "/opt/perf/bin/kit-pull && bash $ES_KIT prepare $PERTURB" 900 >&2 || die 1 "ES 호스트 교란 준비 실패"
}

# perturb_start <cell> <k6 DURATION>: steady면 교란을 SSM으로 띄워 두고 바로 돌아온다
perturb_start() {
  local dur=${2%s} s e for
  if [ -z "$PERTURB" ] || [ "$1" != steady ]; then return 0; fi
  case $dur in ''|*[!0-9]*) die 1 "교란은 초 단위 DURATION에서만 쓴다: $2" ;; esac
  for=${PERTURB_FOR_S:-$((dur - PERTURB_AT_S))}
  [ $((PERTURB_AT_S + for)) -le "$dur" ] && [ "$for" -gt 0 ] || die 1 "교란 구간 ${PERTURB_AT_S}+${for}초가 run ${dur}초 밖이다"
  s=$(( $(now_ms) + PERTURB_AT_S * 1000 )) e=$(( s + for * 1000 ))
  ENV_perturb=$PERTURB
  ENV_perturb_params="value=${PERTURB_VALUE:-na} at=${PERTURB_AT_S}s for=${for}s"
  PERTURB_OUT=$RAW/$RID.perturb.txt
  ssm_run "$ES_ID" "bash $ES_KIT run $PERTURB $s $e $PERTURB_VALUE" $(( (e - s) / 1000 + PERTURB_AT_S + 300 )) > "$PERTURB_OUT" &
  PERTURB_PID=$!
}

# k6가 끝난 뒤: 교란이 끝나기를 기다려 실제 시작과 끝 시각, 쓰기 결과를 env에 적는다. 끝 줄이 없으면 바로 푼다
perturb_stop() {
  local rc=0
  if [ -z "$PERTURB_PID" ]; then return 0; fi
  wait "$PERTURB_PID" || rc=$?
  PERTURB_PID=''
  if is_dry; then return 0; fi
  ENV_perturb_start_ms=$(sed -n 's/^PERTURB_START //p' "$PERTURB_OUT" | tail -n 1)
  ENV_perturb_end_ms=$(sed -n 's/^PERTURB_END //p' "$PERTURB_OUT" | tail -n 1)
  ENV_perturb_result=$(sed -n 's/^PERTURB_RESULT //p' "$PERTURB_OUT" | tail -n 1)
  if [ "$rc" != 0 ] || [ -z "$ENV_perturb_end_ms" ]; then
    warn "$RID 교란이 정상으로 끝나지 않았다(종료 코드 $rc). ES 호스트 교란을 푼다"
    perturb_clear
  fi
}

# 남은 지연과 정지를 푼다. run.sh가 비정상 종료할 때도 부른다
perturb_clear() {
  if [ -z "$PERTURB" ] || [ -z "$ES_ID" ]; then return 0; fi
  ssm_run "$ES_ID" "bash $ES_KIT clear" 300 >&2 || warn "ES 호스트 교란 해제 실패. SSM으로 ES 호스트에서 bash $ES_KIT clear"
}
