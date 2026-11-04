"""raw 결과를 한국어 markdown 표로 정리해 단계 결과 파일에 덧붙인다.

python3 -I tools/summarize.py --step SEARCH-T01 --runs RUN_ID... [--ref RUN_ID...] [--raw DIR] [--out FILE] [--note TEXT]
--raw 기본값은 results/raw, --out이 없으면 표준 출력에 쓴다. 파일이 새로 생기면 머리말부터 쓴다. --note는 블록 머리에 한 줄을 붙인다.
--ref의 run은 표에 쓰지 않고 before의 기준 요청률 출처와 spike 회복 기준을 정할 때만 읽는다.
python3 -I tools/summarize.py --self-test 는 run id 해석, spike 회복 시간과 회복 기준, CloudWatch 구간 선택, 최대 안정 요청률과 구간 표시,
처리 가능 요청률, ramp 단계 순서와 단계별 통과 기준, ramp dropped, 요청당 CPU초 근사 표시, p99 표본 수를 확인한다.
"""
import argparse
import csv
import datetime
import gzip
import json
import math
import os
import re
import statistics
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_RAW = os.path.join(HERE, '..', 'results', 'raw')
RUN_ID = re.compile(r'^(\d{4}-\d{2}-\d{2})-(SEARCH-T\d+)-([^-]+)-(warmup|smoke|ramp|steady|spike|sequential|explain)-(.+)-r(\d+)$')
RDS_VCPUS = {'db.m7g.large': 2, 'db.t4g.micro': 2}
RECOVERY_P95_MS = 500
RECOVERY_MIN_WINDOW_S = 5
RECOVERY_MIN_SAMPLES = 3
RECOVERY_REF_FACTOR = 1.2
P99_MIN_SAMPLES = 100
CLASSES = ['high', 'mid', 'low', 'rare', 'none']
SORTS = ['popular', 'price_asc', 'latest']


def parse_run_id(run_id):
    m = RUN_ID.match(run_id)
    if not m:
        return {}
    date, step, dataset, cell, variant, rep = m.groups()
    return {'date_kst': date, 'step': step, 'dataset': dataset, 'cell': cell, 'variant': variant, 'rep': rep}


def read_env(path):
    env = {}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            for line in f:
                if '=' in line:
                    k, v = line.rstrip('\n').split('=', 1)
                    env[k] = v
    return env


def read_tsv(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def read_csv(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f))


def load_run(raw, run_id):
    base = os.path.join(raw, run_id)
    env = dict(parse_run_id(run_id))
    env.update({k: v for k, v in read_env(base + '.env.txt').items() if v != 'na'})
    summary = None
    if os.path.exists(base + '.json'):
        with open(base + '.json', encoding='utf-8') as f:
            summary = json.load(f)
    ramp = read_tsv(base + '.ramp.tsv')
    window = ms_window(env.get('start_ms'), env.get('end_ms'))
    stages = {}
    # ramp의 .json은 {max_stable_rate, stages:{rate: 요약}}이라 지연, 처리량, CPU 모두 최대 안정 단계 구간에서 읽는다
    if summary and 'stages' in summary and 'metrics' not in summary:
        stages = summary['stages']
        ms = summary.get('max_stable_rate')
        summary = next((v for k, v in summary['stages'].items() if same_rate(k, ms)), None)
    if ramp:
        ms = max_stable({'env': env, 'ramp': ramp})
        row = next((r for r in ramp if same_rate(r['rate'], ms)), {})
        window = ms_window(row.get('start_ms'), row.get('end_ms'))
    return {
        'id': run_id,
        'env': env,
        'summary': summary,
        'window': window,
        'cw': read_csv(base + '.cw.csv'),
        'digest': read_tsv(base + '.digest.txt'),
        'ramp': ramp,
        'stages': stages,
        'points': base + '.points.csv.gz' if os.path.exists(base + '.points.csv.gz') else None,
    }


def same_rate(a, b):
    try:
        return float(a) == float(b)
    except (TypeError, ValueError):
        return False


def ms_window(start_ms, end_ms):
    try:
        return int(start_ms) / 1000, int(end_ms) / 1000
    except (TypeError, ValueError):
        return None


def metric(run, name, stat):
    s = run['summary']
    m = s and s.get('metrics', {}).get(name)
    return m['values'].get(stat) if m else None


def tagged_counts(run, name, tag):
    """`name{tag:value}` 하위 지표의 count. 요약에 없는 값은 other로 합친다."""
    s = run['summary']
    if not s:
        return {}
    out = {}
    prefix = f'{name}{{{tag}:'
    for key, m in s['metrics'].items():
        if key.startswith(prefix) and m['values'].get('count'):
            out[key[len(prefix):-1]] = int(m['values']['count'])
    total = metric(run, name, 'count') or 0
    if total - sum(out.values()) > 0:
        out['other'] = int(total - sum(out.values()))
    return out


def fmt(v, digits=0):
    if v is None:
        return '-'
    return f'{v:,.{digits}f}'


def sample_count(run, name):
    """지연 지표 표본 수. http_req_duration 하위 지표와 같은 태그의 http_reqs count다."""
    return int(metric(run, name.replace('http_req_duration', 'http_reqs', 1), 'count') or 0)


def p99_text(v, n):
    """표본이 P99_MIN_SAMPLES 미만이면 p99 대신 표본 부족을 적는다."""
    return fmt(v) if n >= P99_MIN_SAMPLES else f'표본 부족(n={n})'


def latency(run, name):
    vals = [metric(run, name, s) for s in ('med', 'p(95)', 'p(99)', 'max')]
    if not vals[-1]:
        return '-'
    n = sample_count(run, name)
    text = '/'.join([fmt(vals[0]), fmt(vals[1]), p99_text(vals[2], n), fmt(vals[3])])
    return text if n < P99_MIN_SAMPLES else f'{text} (n={n:,})'


def cw_epoch(iso):
    return datetime.datetime.fromisoformat(iso.replace('Z', '+00:00')).timestamp()


def cw_window(cw, name, window):
    """(평균, 최대, 근사 여부). [start, end]에 온전히 든 1분 지표만 쓰고, 그런 분이 없으면 겹친 초로 가중한 평균을 근사로 낸다."""
    if not window:
        return None, None, False
    start, end = window
    points = {}
    for r in cw:
        if r['metric'] == name and r['value'] not in ('', 'na'):
            points.setdefault(cw_epoch(r['timestamp_iso']), {})[r['stat']] = float(r['value'])
    points = {t: v for t, v in points.items() if 'Average' in v}
    full = [v for t, v in points.items() if t >= start and t + 60 <= end]
    if full:
        return statistics.mean(v['Average'] for v in full), max(v.get('Maximum', v['Average']) for v in full), False
    part = [(min(t + 60, end) - max(t, start), v) for t, v in points.items()]
    part = [(w, v) for w, v in part if w > 0]
    if not part:
        return None, None, False
    mean = sum(w * v['Average'] for w, v in part) / sum(w for w, _ in part)
    return mean, max(v.get('Maximum', v['Average']) for _, v in part), True


def dropped_count(run):
    """dropped_iterations. ramp는 최대 안정 단계의 값이고 SLO를 통과한 단계가 없으면 마지막으로 돈 단계의 값이다."""
    if run['ramp']:
        ms = max_stable(run)
        row = run['ramp'][-1] if same_rate(ms, 0) else next((r for r in run['ramp'] if same_rate(r['rate'], ms)), {})
        return row.get('dropped') or '-'
    return metric(run, 'dropped_iterations', 'count') or int(run['env'].get('dropped_iterations') or 0)


def cpu_seconds_per_request(run):
    """(요청당 RDS CPU초, 근사 여부). 평균 CPU% / 100 x vCPU / 구간 초당 요청 수.
    일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 근사가 아니다."""
    mean, _, approx = cw_window(run['cw'], 'CPUUtilization', run['window'])
    vcpus = RDS_VCPUS.get(run['env'].get('rds_class'))
    reqs = metric(run, 'http_reqs', 'count')
    if mean is None or not vcpus or not reqs:
        return None, False
    start, end = run['window']
    failed = metric(run, 'http_req_failed', 'rate')
    exact = (run['env'].get('cell') == 'steady' and run['env'].get('scenario') == 'rate'
             and not dropped_count(run) and failed is not None and failed < 0.01)
    return mean / 100 * vcpus / (reqs / (end - start)), approx or not exact


def approx_text(v, approx, digits):
    if v is None:
        return '-'
    return ('≈' if approx else '') + f'{v:,.{digits}f}'


def digest_label(text):
    t = text.strip()
    lower = t.lower()
    m = re.search(r'\bfrom\s+`?(\w+)`?', lower)
    table = m.group(1) if m else '?'
    head = lower.split(' from ')[0]
    if re.search(r'\bcount\s*\(', head):
        return f'COUNT {table}'
    return f'{lower.split()[0].upper() if lower else "?"} {table}'


def top_digests(run, n=3):
    rows = run['digest']
    try:
        total = sum(float(r['sum_timer_wait_ms']) for r in rows)
    except (KeyError, ValueError):
        return '-'
    if not total:
        return '-'
    parts = [f'{digest_label(r["digest_text"])} {float(r["sum_timer_wait_ms"]) / total * 100:.0f}%' for r in rows[:n]]
    return ', '.join(parts)


def counts_text(counts):
    return ' '.join(f'{k}:{v}' for k, v in sorted(counts.items())) or '-'


def bp_text(env):
    v = env.get('bp_reads_delta')
    if v is None:
        return '-'
    return f'{v} (디스크 읽기)' if env.get('bp_disk_read_flag') in ('1', 'true', 'yes') else v


def main_row(run, base_note=''):
    """base_note가 있으면 steady와 spike 행의 셀 이름에 붙인다."""
    e = run['env']
    if e.get('skip_reason'):
        cells = [f'`{run["id"]}`', e.get('cell', '-'), e.get('variant', '-'), e.get('dataset', '-'),
                 f'건너뜀: {e["skip_reason"]}'] + ['-'] * 15
        return '| ' + ' | '.join(cells) + ' |'
    kit = (run['summary'] or {}).get('kit', {})
    target = e.get('req_rate') or (kit.get('req_rate') if kit.get('scenario') in ('rate', 'spike') else None)
    if run['ramp']:
        target = ramp_interval(run)
    cpu_avg, cpu_max, cpu_approx = cw_window(run['cw'], 'CPUUtilization', run['window'])
    conn_avg, conn_max, _ = cw_window(run['cw'], 'DatabaseConnections', run['window'])
    cpu_req, req_approx = cpu_seconds_per_request(run)
    cw_note = ' (CW 미완)' if e.get('cw_complete') == 'false' else ''
    failed = metric(run, 'http_req_failed', 'rate')
    dropped = dropped_count(run)
    cell = e.get('cell', '-')
    if e.get('k6_exit') not in (None, '0'):
        cell += f' (k6 종료 {e["k6_exit"]})'
    if run['ramp'] and same_rate(max_stable(run), 0):
        cell += ' (dropped는 마지막 단계)'
    if base_note and e.get('cell') in ('steady', 'spike'):
        cell += f' ({base_note})'
    cells = [
        f'`{run["id"]}`', cell, e.get('variant', '-'), e.get('dataset', '-'),
        str(target) if target is not None else '-',
        fmt(metric(run, 'http_reqs', 'rate'), 2),
        latency(run, 'http_req_duration{kind:first}'),
        latency(run, 'http_req_duration'),
        '-' if failed is None else f'{failed * 100:.2f}%',
        counts_text(tagged_counts(run, 'resp_status', 'status')),
        counts_text(tagged_counts(run, 'error_code', 'code')),
        f'{dropped} (부하 미달)' if dropped and e.get('cell') == 'spike' else str(dropped),
        '-' if cpu_avg is None else f'{approx_text(cpu_avg, cpu_approx, 1)}/{cpu_max:.1f}{cw_note}',
        '-' if conn_avg is None else f'{conn_avg:.0f}/{conn_max:.0f}',
        approx_text(cpu_req, req_approx, 4) + (cw_note if cpu_req is not None else ''),
        bp_text(e),
        top_digests(run),
        e.get('git_rev', '-')[:12],
        e.get('jar_sha256', '-')[:8],
        e.get('applied_ddl', '-') or '-',
    ]
    return '| ' + ' | '.join(cells) + ' |'


MAIN_HEADER = [
    '| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) '
    '| 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 '
    '| 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |',
    '|' + '---|' * 20,
]


def steady_section(runs):
    groups = {}
    for r in runs:
        if r['env'].get('cell') == 'steady':
            p95 = metric(r, 'http_req_duration{kind:first}', 'p(95)')
            if p95 is not None:
                groups.setdefault((r['env'].get('variant', '-'), r['env'].get('dataset', '-')), []).append(p95)
    if not groups:
        return []
    out = ['### steady 첫 페이지 p95', '',
           '| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |', '|---|---|---|---|---|---|']
    for (variant, dataset), vals in groups.items():
        med = statistics.median(vals)
        band = (max(vals) - min(vals)) / med * 100 if med else 0
        out.append(f'| {variant} | {dataset} | {len(vals)} | {med:.0f} | {min(vals):.0f}~{max(vals):.0f} | {band:.1f}% |')
    return out + ['']


def passed(row):
    return row.get('pass') in ('1', 'true', 'yes')


def max_stable(run):
    """env의 max_stable_rate. 없으면 ramp.tsv에서 첫 단계부터 처음 실패하기 전 단계, 첫 단계가 실패하면
    그보다 낮은 바닥 단계 중 처음 통과한 단계, 없으면 0."""
    if run['env'].get('max_stable_rate'):
        return run['env']['max_stable_rate']
    rows = run['ramp']
    if not rows:
        return '0'
    if not passed(rows[0]):
        first = float(rows[0]['rate'])
        return next((r['rate'] for r in rows[1:] if float(r['rate']) < first and passed(r)), '0')
    best = rows[0]['rate']
    for row in rows[1:]:
        if not passed(row):
            break
        best = row['rate']
    return best


def capacity_ok(row):
    """p95를 빼고 실패율 1% 미만, dropped 0, k6 종료 0인 단계."""
    try:
        return float(row['failed_pct']) < 1 and row['dropped'] == '0' and row.get('k6_exit', '0') in ('0', '')
    except (KeyError, ValueError):
        return False


def capacity(run):
    """env의 capacity_rate. 없으면 ramp.tsv에서 처리 기준을 지킨 가장 높은 단계, 없으면 0."""
    if run['env'].get('capacity_rate'):
        return run['env']['capacity_rate']
    best = '0'
    for row in run['ramp']:
        if capacity_ok(row) and float(row['rate']) > float(best):
            best = row['rate']
    return best


def ramp_interval(run, unit=''):
    """대표 지표 표시. 최대 안정 단계와 그보다 높은 단계 중 처음 실패한 단계, 최대 안정 요청률이 0이면 SLO 미충족과 처리 가능 요청률."""
    ms = max_stable(run)
    if same_rate(ms, 0):
        return f'SLO 미충족 (처리 가능 {capacity(run)})'
    failed = [float(row['rate']) for row in run['ramp'] if not passed(row) and float(row['rate']) > float(ms)]
    return f'{ms}{unit} ({min(failed):g}에서 실패)' if failed else f'{ms}{unit} (실패 단계 없음)'


def ramp_section(runs):
    out = []
    for r in runs:
        if not r['ramp']:
            continue
        ms = max_stable(r)
        if same_rate(ms, 0):
            head = (f'SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. '
                    f'처리 가능 요청률은 {capacity(r)} req/s다.')
        else:
            head = (f'최대 안정 요청률 {ramp_interval(r, " req/s")}. 대표 행은 {ms} req/s 단계 구간의 값이다. '
                    f'처리 가능 요청률은 {capacity(r)} req/s다.')
        out += [f'### ramp `{r["id"]}`', '', head, '',
                '| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |',
                '|---|---|---|---|---|---|---|---|---|']
        for row in r['ramp']:
            ok = 'SLO' if passed(row) else '처리 기준만' if capacity_ok(row) else '없음'
            cpu, _, approx = cw_window(r['cw'], 'CPUUtilization', ms_window(row.get('start_ms'), row.get('end_ms')))
            stage = next((v for k, v in r.get('stages', {}).items() if same_rate(k, row['rate'])), None)
            n = sample_count({'summary': stage}, 'http_req_duration{kind:first}')
            p99 = row['first_p99_ms'] if n >= P99_MIN_SAMPLES else p99_text(None, n)
            if n >= P99_MIN_SAMPLES:
                p99 += f' (n={n:,})'
            out.append(f'| {row["rate"]} | {row["http_reqs_per_s"]} | {row["first_p95_ms"]} | {p99} '
                       f'| {row["failed_pct"]} | {row["dropped"]} | {approx_text(cpu, approx, 1)} | {row.get("k6_exit") or "-"} | {ok} |')
        out.append('')
    return out


def p95(vals):
    s = sorted(vals)
    pos = 0.95 * (len(s) - 1)
    lo = int(pos)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (pos - lo)


def read_points(path):
    """k6 csv에서 (시작 시각 초, [(초, ms, scenario, 성공 여부)]) 를 읽는다. 시각은 unix 초나 밀리초 모두 받는다."""
    t0 = None
    samples = []
    with gzip.open(path, 'rt', encoding='utf-8', newline='') as f:
        for row in csv.DictReader(f):
            ts = float(row['timestamp'])
            ts = ts / 1000 if ts > 1e11 else ts
            t0 = ts if t0 is None else min(t0, ts)
            if row['metric_name'] == 'http_req_duration':
                samples.append((ts, float(row['metric_value']), row['scenario'], row.get('expected_response') != 'false'))
    return t0, samples


def recovery_window(probe_rate):
    """회복 창 길이(초). 탐침 요청률에서 표본 3개가 들어오는 길이이고 5초보다 짧지 않다."""
    return max(RECOVERY_MIN_WINDOW_S, math.ceil(RECOVERY_MIN_SAMPLES / float(probe_rate)))


def recovery_seconds(path, spike_seconds, probe_rate=2, threshold=RECOVERY_P95_MS, probe_seconds=None):
    """burst가 끝난 뒤 probe 요청의 직전 회복 창이 실패 없이 표본 3개 이상이고 p95가 처음 회복 기준(ms) 아래로 내려온 시점(초).
    탐침 시간(probe_seconds)이 끝난 뒤 들어온 응답으로는 판정하지 않는다. 끝까지 못 내려오면 None."""
    window = recovery_window(probe_rate)
    t0, samples = read_points(path)
    probe = [(ts, ms, ok) for ts, ms, sc, ok in samples if sc == 'probe']
    if not probe:
        return None
    burst_end = t0 + spike_seconds
    last = max(ts for ts, _, _ in probe)
    end = last + 1 if probe_seconds is None else min(last + 1, burst_end + float(probe_seconds))
    t = burst_end + 1
    while t <= end:
        win = [(ms, ok) for ts, ms, ok in probe if t - window < ts <= t]
        vals = [ms for ms, _ in win]
        if len(vals) >= RECOVERY_MIN_SAMPLES and all(ok for _, ok in win) and p95(vals) < threshold:
            return round(t - burst_end)
        t += 1
    return None


def steady_p95s(runs, variant, dataset):
    return [v for r in runs if r['env'].get('cell') == 'steady' and r['env'].get('variant') == variant
            and r['env'].get('dataset') == dataset
            for v in [metric(r, 'http_req_duration{kind:first}', 'p(95)')] if v is not None]


def recovery_threshold(run, pool):
    """회복 기준 p95(ms). max(500, 1.2 x 기준 steady 첫 페이지 p95 중앙값)이고 기준은 같은 데이터의 before,
    before steady가 없으면 그 변형 자신이다. steady가 없으면 500."""
    e = run['env']
    vals = steady_p95s(pool, 'before', e.get('dataset')) or steady_p95s(pool, e.get('variant'), e.get('dataset'))
    if not vals:
        return RECOVERY_P95_MS
    return max(RECOVERY_P95_MS, RECOVERY_REF_FACTOR * statistics.median(vals))


def spike_section(runs, ref=()):
    out = []
    for r in runs:
        if r['env'].get('cell') != 'spike' or r['env'].get('skip_reason'):
            continue
        kit = (r['summary'] or {}).get('kit', {})
        probe_rate = r['env'].get('probe_rate') or kit.get('probe_rate', 2)
        window = recovery_window(probe_rate)
        threshold = recovery_threshold(r, list(runs) + list(ref))
        rec = recovery_seconds(r['points'], kit.get('spike_seconds', 30), probe_rate, threshold,
                               kit.get('probe_seconds', 90)) if r['points'] else None
        dropped = metric(r, 'dropped_iterations', 'count') or int(r['env'].get('dropped_iterations') or 0)
        burst = metric(r, 'http_req_failed{phase:burst}', 'rate')
        probe = metric(r, 'http_req_failed{phase:probe}', 'rate')
        if not out:
            out = ['### spike', '', '| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |',
                   '|---|---|---|---|---|---|---|---|']
        rec_text = '-' if not r['points'] else (
            (f'{rec}초' if rec is not None else f'{kit.get("probe_seconds", 90)}초 안에 회복 안 됨') + f' (회복 창 {window}s)')
        drop_text = f'{dropped} (부하 미달)' if dropped else '0'
        out.append(f'| `{r["id"]}` | {r["env"].get("req_rate") or kit.get("req_rate", "-")} '
                   f'| {probe_rate} '
                   f'| {"-" if burst is None else f"{burst * 100:.2f}%"} | {"-" if probe is None else f"{probe * 100:.2f}%"} '
                   f'| {threshold:.0f}ms | {rec_text} | {drop_text} |')
    return out + [''] if out else []


def sequential_section(runs):
    out = []
    for r in runs:
        if r['env'].get('cell') != 'sequential' or not r['summary']:
            continue
        out += [f'### sequential `{r["id"]}`', '', '| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |', '|---|---|---|---|---|---|']
        for cls in CLASSES:
            for sort in SORTS:
                for page in ('1', '2'):
                    name = f'http_req_duration{{kind:seq,kw_class:{cls},sort:{sort},page:{page}}}'
                    if metric(r, name, 'max'):
                        out.append(f'| {cls} | {sort} | {page} | {fmt(metric(r, name, "med"))} '
                                   f'| {fmt(metric(r, name, "p(90)"))} | {fmt(metric(r, name, "max"))} |')
        out.append('')
    return out


BASE_SOURCES = {'own': '자기 ramp의 최대 안정 요청률', 'before': 'before의 기준 요청률', 'manual': '직접 지정',
                'capacity': 'SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀'}


def render(step, runs, new_file, notes=(), ref=()):
    out = []
    if new_file:
        out += [f'# {step} 측정 결과', '',
                '지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, '
                '요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. '
                '≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 '
                'dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.', '']
    first = runs[0]['env']
    variants = ' '.join(dict.fromkeys(r['env'].get('variant', '-') for r in runs))
    out += [f'## {first.get("date_kst", "-")} {first.get("dataset", "-")} {variants}', '']
    out += [n for note in notes for n in (note, '')]
    bases = {}
    for r in list(ref) + list(runs):
        if r['env'].get('base_rate'):
            bases.setdefault(r['env'].get('variant', '-'), (r['env']['base_rate'], r['env'].get('base_rate_source', '-')))
    # 기준 요청률이 처리 가능 요청률에서 왔거나 그런 before에서 넘어온 변형은 SLO 미충족 비교 부하로 표시한다
    notes_by_variant = {}
    shown = dict.fromkeys(r['env'].get('variant', '-') for r in runs)
    for variant, (base, src) in bases.items():
        cap = src == 'capacity' or (src == 'before' and bases.get('before', (None, None))[1] == 'capacity')
        if cap:
            notes_by_variant[variant] = f'SLO 미충족, 비교 부하 {base} req/s'
        if variant not in shown:
            continue
        rule = 'steady와 spike는 건너뛰었다' if same_rate(base, 0) else 'steady는 0.7배, spike는 3배로 돌렸다'
        label = BASE_SOURCES.get(src, src) + (', before가 SLO 미충족이라 비교 부하' if cap and src == 'before' else '')
        out += [f'{variant} 기준 요청률 {base} req/s ({label}). {rule}.', '']
    out += MAIN_HEADER + [main_row(r, notes_by_variant.get(r['env'].get('variant'), '')) for r in runs] + ['']
    out += steady_section(runs) + ramp_section(runs) + spike_section(runs, ref) + sequential_section(runs)
    return '\n'.join(out)


def self_test():
    assert parse_run_id('2026-11-05-SEARCH-T01-100k-steady-base-r2') == {
        'date_kst': '2026-11-05', 'step': 'SEARCH-T01', 'dataset': '100k', 'cell': 'steady', 'variant': 'base', 'rep': '2'}
    assert parse_run_id('2026-11-05-SEARCH-T02-1m-spike-idx-a-b-r1')['variant'] == 'idx-a-b'
    assert abs(p95(list(range(1, 101))) - 95.05) < 1e-9
    header = 'metric_name,timestamp,metric_value,check,error,error_code,expected_response,group,method,name,proto,scenario,service,status,subproto,tls_version,url,extra_tags,metadata\n'

    EPOCH = 1793708145

    def write(rows, unit=1):
        f = tempfile.NamedTemporaryFile(suffix='.csv.gz', delete=False)
        with gzip.open(f.name, 'wt') as g:
            g.write(header)
            for ts, ms, sc, *rest in rows:
                ok = rest[0] if rest else True
                g.write(f'http_req_duration,{int((EPOCH + ts) * unit)},{ms},,,,{"true" if ok else "false"},,GET,u,HTTP/1.1,{sc},,{200 if ok else 0},,,u,,\n')
        return f.name

    # burst 0~10초, probe는 10초부터 초당 2건. 16초까지는 2000ms, 17초부터 50ms
    rows = [(t / 10, 3000, 'burst') for t in range(100)]
    rows += [(10 + i // 2, 2000 if 10 + i // 2 < 17 else 50, 'probe') for i in range(40)]
    for unit in (1, 1000):
        path = write(rows, unit)
        try:
            # 직전 5초 창이 16초의 느린 표본을 벗어나는 21초가 회복 시점이라 burst 끝에서 11초다
            assert recovery_seconds(path, 10) == 11, recovery_seconds(path, 10)
        finally:
            os.unlink(path)
    path = write([(t, 2000, 'probe') for t in range(10, 30)])
    try:
        assert recovery_seconds(path, 10) is None
    finally:
        os.unlink(path)

    # 느린 탐침은 표본 3개가 들어오도록 창을 넓힌다. 0.25 req/s는 12초, 0.125 req/s는 24초
    assert (recovery_window(2), recovery_window(0.5), recovery_window('0.25'), recovery_window(0.125)) == (5, 6, 12, 24)
    # 0.25 req/s 탐침이 4초마다. 26초까지 2000ms, 30초부터 50ms라 (26, 38] 창이 처음 빠른 표본 3개만 담는 38초가 회복 시점이다
    path = write([(t / 10, 3000, 'burst') for t in range(100)] + [(t, 2000 if t < 30 else 50, 'probe') for t in range(10, 100, 4)])
    try:
        assert recovery_seconds(path, 10, 0.25) == 28, recovery_seconds(path, 10, 0.25)
        # 예전 5초 창은 표본이 2개 이하라 회복을 잴 수 없었다
        assert recovery_seconds(path, 10, 2) is None
    finally:
        os.unlink(path)
    # 0.125 req/s 탐침이 8초마다 90초 내내 느리면 회복 안 됨이고 표에 관찰 한도와 창을 적는다
    path = write([(t / 10, 3000, 'burst') for t in range(300)] + [(t, 2000, 'probe') for t in range(30, 120, 8)])
    try:
        assert recovery_seconds(path, 30, 0.125) is None
        spike = {'id': 's', 'env': {'cell': 'spike', 'probe_rate': '0.125'}, 'points': path,
                 'summary': {'metrics': {}, 'kit': {'spike_seconds': 30, 'probe_seconds': 90}}}
        assert '| 90초 안에 회복 안 됨 (회복 창 24s) |' in '\n'.join(spike_section([spike])), spike_section([spike])
    finally:
        os.unlink(path)

    # 실패한 탐침은 지연이 회복 기준 아래여도 회복이 아니다. 30초 타임아웃 셋이 31.2초 기준 아래이거나 50ms 실패가 셋이어도 None
    path = write([(t / 10, 3000, 'burst') for t in range(300)] + [(t, 30000, 'probe', False) for t in range(31, 120, 8)])
    try:
        assert recovery_seconds(path, 30, 0.125, 31200) is None
    finally:
        os.unlink(path)
    path = write([(t / 10, 3000, 'burst') for t in range(100)] + [(10 + i / 2, 50, 'probe', False) for i in range(40)])
    try:
        assert recovery_seconds(path, 10, 2) is None
    finally:
        os.unlink(path)
    # 탐침 90초가 끝난 뒤 늦게 들어온 빠른 응답으로는 회복을 판정하지 않는다
    path = write([(t / 10, 3000, 'burst') for t in range(300)] + [(t, 2000, 'probe') for t in range(31, 120, 2)]
                 + [(t, 50, 'probe') for t in range(121, 140)])
    try:
        assert recovery_seconds(path, 30, 2, 500, 90) is None
        assert recovery_seconds(path, 30, 2, 500) is not None
    finally:
        os.unlink(path)

    # CloudWatch 1분 지표. 09:30 앞뒤는 유휴 2%, 09:30 30%, 09:31 40%, 09:32 50%
    t0 = cw_epoch('2026-11-05T09:30:00Z')
    cw = []
    for i, avg in [(-1, 2), (0, 30), (1, 40), (2, 50), (3, 2), (4, 2)]:
        ts = datetime.datetime.fromtimestamp(t0 + 60 * i, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S+00:00')
        cw += [{'timestamp_iso': ts, 'metric': 'CPUUtilization', 'stat': 'Average', 'value': str(avg)},
               {'timestamp_iso': ts, 'metric': 'CPUUtilization', 'stat': 'Maximum', 'value': str(avg + 5)}]
    # 분 경계에 맞춘 180초: 09:30, 09:31, 09:32가 온전히 든다
    assert cw_window(cw, 'CPUUtilization', (t0, t0 + 180)) == (40, 55, False)
    # 30초 밀린 180초: 09:31, 09:32만 온전히 든다
    assert cw_window(cw, 'CPUUtilization', (t0 + 30, t0 + 210)) == (45, 55, False)
    # 45초 단계: 온전한 분이 없어 09:30 20초와 09:31 25초로 가중한다
    mean, mx, approx = cw_window(cw, 'CPUUtilization', (t0 + 40, t0 + 85))
    assert approx and abs(mean - (20 * 30 + 25 * 40) / 45) < 1e-9 and mx == 45, (mean, mx, approx)
    run = {'cw': cw, 'window': (t0, t0 + 180), 'ramp': [],
           'env': {'rds_class': 'db.m7g.large', 'cell': 'steady', 'scenario': 'rate', 'dropped_iterations': '0'},
           'summary': {'metrics': {'http_reqs': {'values': {'count': 900}}, 'http_req_failed': {'values': {'rate': 0.002}}}}}
    # 40% x 2 vCPU / 초당 5건 = 0.16초
    v, approx = cpu_seconds_per_request(run)
    assert abs(v - 0.16) < 1e-9 and not approx, v
    # spike, dropped가 있는 steady, 실패율 1% 이상인 steady는 ≈를 붙인다
    for env, failed in [({'cell': 'spike', 'scenario': 'spike'}, 0.002), ({'dropped_iterations': '3'}, 0.002), ({}, 0.02)]:
        r = dict(run, env=dict(run['env'], **env))
        r['summary'] = {'metrics': dict(run['summary']['metrics'], http_req_failed={'values': {'rate': failed}})}
        assert cpu_seconds_per_request(r)[1], env
    run['env']['rds_class'] = 'db.r6g.large'
    assert cpu_seconds_per_request(run) == (None, False)

    # 최대 안정 요청률: 올라가다 실패, 바닥 단계에서 통과, 모두 실패
    def ramp(*rows):
        return {'env': {}, 'ramp': [{'rate': r, 'pass': p} for r, p in rows]}
    assert max_stable(ramp(('2', '1'), ('4', '1'), ('8', '0'))) == '4'
    assert max_stable(ramp(('2', '0'), ('1', '0'), ('0.5', '1'))) == '0.5'
    assert max_stable(ramp(('2', '0'), ('1', '0'), ('0.5', '0'), ('0.25', '0'))) == '0'

    # ramp 대표 행의 dropped는 최대 안정 단계 값이고 실패한 단계의 dropped를 더하지 않는다
    def ramp_run(ms, *rows):
        return {'id': 'r', 'env': {'cell': 'ramp', 'max_stable_rate': ms, 'dropped_iterations': '45'},
                'summary': None, 'window': None, 'cw': [], 'digest': [],
                'ramp': [{'rate': r, 'pass': p, 'dropped': d} for r, p, d in rows]}

    def dropped_col(run):
        cells = main_row(run).split(' | ')
        return cells[1], cells[11]
    assert dropped_col(ramp_run('4', ('2', '1', '0'), ('4', '1', '0'), ('8', '0', '45'))) == ('ramp', '0')
    # 통과한 단계가 없으면 마지막으로 돈 단계의 dropped를 적고 셀 이름에 표시한다
    assert dropped_col(ramp_run('0', ('2', '0', '30'), ('1', '0', '10'), ('0.5', '0', '5'))) == ('ramp (dropped는 마지막 단계)', '5')

    # 대표 지표 구간: 최대 안정 단계와 그 위에서 처음 실패한 단계, 실패 단계가 없으면 그렇게 적는다
    def tsv_run(env, *rows):
        return {'id': 'x', 'env': dict({'cell': 'ramp', 'variant': 'before', 'dataset': 'local'}, **env),
                'summary': None, 'window': None, 'cw': [], 'digest': [], 'stages': {},
                'ramp': [{'rate': r, 'pass': p, 'failed_pct': f, 'dropped': d, 'k6_exit': k, 'first_p95_ms': '1',
                          'first_p99_ms': '1', 'http_reqs_per_s': r} for r, p, f, d, k in rows]}
    assert ramp_interval(tsv_run({}, ('2', '1', '0', '0', '0'), ('4', '1', '0', '0', '0'), ('8', '1', '0', '0', '0'),
                                 ('16', '0', '0', '0', '0'))) == '8 (16에서 실패)'
    assert ramp_interval(tsv_run({}, ('2', '1', '0', '0', '0'), ('4', '1', '0', '0', '0'))) == '4 (실패 단계 없음)'
    assert ramp_interval(tsv_run({}, ('2', '0', '0', '0', '0'), ('1', '0', '0', '0', '0'), ('0.5', '1', '0', '0', '0'))) == '0.5 (1에서 실패)'

    # SLO는 모든 단계에서 실패하고 0.5와 0.25만 처리 기준을 통과하면 처리 가능 요청률 0.5가 비교 부하다
    floor = [('2', '0', '30.5', '12', '0'), ('1', '0', '2.1', '3', '0'), ('0.5', '0', '0', '0', '0'), ('0.25', '0', '0', '0', '0')]
    cap_ramp = tsv_run({}, *floor)
    assert capacity(cap_ramp) == '0.5' and ramp_interval(cap_ramp) == 'SLO 미충족 (처리 가능 0.5)'
    # k6 안전 중단(99)이나 dropped가 있는 단계는 처리 가능 요청률이 아니다
    assert capacity(tsv_run({}, ('1', '0', '0', '0', '99'), ('0.5', '0', '0', '2', '0'), ('0.25', '0', '0', '0', '0'))) == '0.25'
    env_cap = {'max_stable_rate': '0', 'capacity_rate': '0.5', 'base_rate': '0.5', 'base_rate_source': 'capacity'}
    cap_ramp['env'].update(env_cap)
    steady = {'id': 'st', 'env': dict(env_cap, cell='steady', variant='before', dataset='local', req_rate='0.35'),
              'summary': None, 'window': None, 'cw': [], 'digest': [], 'ramp': [], 'stages': {}}
    text = render('SEARCH-T00', [cap_ramp, steady], False)
    assert 'before 기준 요청률 0.5 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배' in text, text
    assert '| steady (SLO 미충족, 비교 부하 0.5 req/s) |' in text, text
    assert '| ramp (dropped는 마지막 단계) | before | local | SLO 미충족 (처리 가능 0.5) |' in text, text
    # before에서 처리 가능 요청률을 넘겨받은 after도 같은 표시를 한다
    after = dict(steady, env=dict(steady['env'], variant='after', base_rate_source='before'))
    text = render('SEARCH-T00', [after], False, ref=[cap_ramp])
    assert 'after 기준 요청률 0.5 req/s (before의 기준 요청률, before가 SLO 미충족이라 비교 부하)' in text, text
    assert '| steady (SLO 미충족, 비교 부하 0.5 req/s) |' in text and 'before 기준' not in text, text
    # 처리 기준도 통과한 단계가 없으면 기준 요청률 0이고 steady와 spike는 건너뛴다
    zero = tsv_run({'max_stable_rate': '0', 'capacity_rate': '0', 'base_rate': '0', 'base_rate_source': 'own'},
                   *[(r, '0', '5', '0', '0') for r in ('2', '1', '0.5', '0.25')])
    skipped = dict(steady, env={'cell': 'steady', 'variant': 'before', 'dataset': 'local', 'base_rate': '0',
                                'base_rate_source': 'own', 'skip_reason': '기준 요청률 0'})
    text = render('SEARCH-T00', [zero, skipped], False)
    assert capacity(zero) == '0' and 'SLO 미충족 (처리 가능 0)' in text and 'steady와 spike는 건너뛰었다' in text, text

    # 회복 기준: before steady 첫 페이지 p95 중앙값 5000ms의 1.2배인 6000ms를 before와 after spike에 같이 쓴다
    def lat_run(variant, cell, p95v, count=None, **env):
        m = {'http_req_duration{kind:first}': {'values': {'med': 10, 'p(95)': p95v, 'p(99)': p95v + 1, 'max': p95v + 2}}}
        if count is not None:
            m['http_reqs{kind:first}'] = {'values': {'count': count}}
        return {'id': f'{variant}-{cell}', 'env': dict({'cell': cell, 'variant': variant, 'dataset': 'local'}, **env),
                'summary': {'metrics': m, 'kit': {'spike_seconds': 10, 'probe_seconds': 20}},
                'window': None, 'cw': [], 'digest': [], 'ramp': [], 'stages': {}, 'points': None}
    before = [lat_run('before', 'steady', v) for v in (4800, 5000, 5300)]
    assert recovery_threshold(lat_run('before', 'spike', 0), before) == 6000
    assert recovery_threshold(lat_run('after', 'spike', 0), [lat_run('after', 'steady', 100)] + before) == 6000
    # before가 없으면 자기 steady, steady가 없으면 500ms
    assert recovery_threshold(lat_run('base', 'spike', 0), [lat_run('base', 'steady', 1000)]) == 1200
    assert recovery_threshold(lat_run('base', 'spike', 0), [lat_run('base', 'steady', 100)]) == 500
    assert recovery_threshold(lat_run('base', 'spike', 0), []) == 500
    # 5500ms로 머무는 탐침은 500ms 기준으로는 회복 안 됨이고 6000ms 기준으로는 창이 차는 시점에 회복이다
    path = write([(t / 10, 9000, 'burst') for t in range(100)] + [(10 + i / 2, 5500, 'probe') for i in range(20)])
    try:
        assert recovery_seconds(path, 10, 2) is None
        assert recovery_seconds(path, 10, 2, 6000) == 1
        spike_after = dict(lat_run('after', 'spike', 0, probe_rate='2'), points=path)
        assert '| 6000ms | 1초 (회복 창 5s) |' in '\n'.join(spike_section([spike_after], before)), spike_section([spike_after], before)
    finally:
        os.unlink(path)

    # p99는 표본 100개 이상일 때만 보이고 n을 함께 적는다
    assert latency(lat_run('a', 'steady', 200, 40), 'http_req_duration{kind:first}') == '10/200/표본 부족(n=40)/202'
    assert latency(lat_run('a', 'steady', 200, 1500), 'http_req_duration{kind:first}') == '10/200/201/202 (n=1,500)'
    assert latency(lat_run('a', 'steady', 200), 'http_req_duration{kind:first}') == '10/200/표본 부족(n=0)/202'
    # v2 ramp: SLO 실패 뒤에도 처리 기준을 지키는 동안 올라가고 바닥 단계는 첫 단계가 처리 기준을 못 지킬 때만 돈다
    # (a) 2는 p95만 실패, 4는 처리 기준만 통과, 8은 처리 기준 실패
    aws = tsv_run({}, ('2', '0', '0', '0', '0'), ('4', '0', '0', '0', '0'), ('8', '0', '40', '5', '0'))
    assert (max_stable(aws), capacity(aws), ramp_interval(aws)) == ('0', '4', 'SLO 미충족 (처리 가능 4)')
    # 2에서 SLO를 다시 지키지 못한 4가 우연히 통과해도 첫 SLO 실패 뒤라 최대 안정 요청률을 올리지 않는다
    assert max_stable(tsv_run({}, ('2', '0', '0', '0', '0'), ('4', '1', '0', '0', '0'), ('8', '0', '40', '5', '0'))) == '0'
    table = '\n'.join(ramp_section([aws]))
    assert '| 2 | 2 | 1 | 표본 부족(n=0) | 0 | 0 | - | 0 | 처리 기준만 |' in table and '| 없음 |' in table, table
    # (b) 2와 4는 SLO 통과, 8은 SLO만 실패, 16은 처리 기준 실패
    mid = tsv_run({}, ('2', '1', '0', '0', '0'), ('4', '1', '0', '0', '0'), ('8', '0', '0', '0', '0'), ('16', '0', '3', '0', '0'))
    assert (max_stable(mid), capacity(mid), ramp_interval(mid)) == ('4', '8', '4 (8에서 실패)')
    assert '최대 안정 요청률 4 req/s (8에서 실패). 대표 행은 4 req/s 단계 구간의 값이다. 처리 가능 요청률은 8 req/s다.' in '\n'.join(ramp_section([mid]))
    # (c) 2는 처리 기준 실패, 바닥 1 실패, 0.5 SLO 통과
    low = tsv_run({}, ('2', '0', '20', '0', '0'), ('1', '0', '2', '0', '0'), ('0.5', '1', '0', '0', '0'))
    assert (max_stable(low), capacity(low), ramp_interval(low)) == ('0.5', '0.5', '0.5 (1에서 실패)')

    stage_run = tsv_run({'max_stable_rate': '2'}, ('2', '1', '0', '0', '0'), ('4', '0', '0', '0', '0'))
    stage_run['stages'] = {'2': {'metrics': {'http_reqs{kind:first}': {'values': {'count': 60}}}},
                           '4': {'metrics': {'http_reqs{kind:first}': {'values': {'count': 120}}}}}
    table = '\n'.join(ramp_section([stage_run]))
    assert '최대 안정 요청률 2 req/s (4에서 실패).' in table
    assert '| 2 | 2 | 1 | 표본 부족(n=60) |' in table and '| 4 | 4 | 1 | 1 (n=120) |' in table, table
    print('self-test ok')


def main():
    if sys.argv[1:] == ['--self-test']:
        return self_test()
    p = argparse.ArgumentParser()
    p.add_argument('--step', required=True)
    p.add_argument('--runs', nargs='+', required=True)
    p.add_argument('--ref', nargs='+', default=[])
    p.add_argument('--raw', default=DEFAULT_RAW)
    p.add_argument('--out')
    p.add_argument('--note', action='append', default=[])
    a = p.parse_args()
    runs = [load_run(a.raw, rid) for rid in a.runs]
    ref = [load_run(a.raw, rid) for rid in a.ref]
    missing = [r['id'] for r in runs if not (r['summary'] or r['ramp'] or r['env'].get('start_ms') or r['env'].get('skip_reason'))]
    if missing:
        sys.exit(f'no raw files for: {" ".join(missing)} in {a.raw}')
    if not a.out:
        print(render(a.step, runs, True, a.note, ref))
        return
    new_file = not os.path.exists(a.out) or os.path.getsize(a.out) == 0
    with open(a.out, 'a', encoding='utf-8') as f:
        f.write(('' if new_file else '\n') + render(a.step, runs, new_file, a.note, ref) + '\n')


if __name__ == '__main__':
    main()
