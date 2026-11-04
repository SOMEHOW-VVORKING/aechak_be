"""raw 결과에서 SVG 그래프를 그린다(ramp 부하 대 p95, 변형별 steady p95 범위, spike 시간축).

tools/.venv/bin/python tools/graphs.py [--raw DIR] [--out DIR] [--step SEARCH-Txx]
--raw 기본값 results/raw, --out 기본값 results/graphs. 파일 이름은 <step>-ramp.svg, <step>-steady.svg, <run-id>-spike.svg.
"""
import argparse
import os
import statistics
import sys

import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import FixedLocator, FuncFormatter, NullFormatter  # noqa: E402

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import summarize as s  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
COLORS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948']
INK = '#3d3d3a'
GRID = '#e4e3dd'
plt.rcParams.update({
    'svg.fonttype': 'none',
    'svg.hashsalt': 'aechak-search-perf',
    'font.size': 10,
    'axes.edgecolor': GRID,
    'axes.labelcolor': INK,
    'xtick.color': INK,
    'ytick.color': INK,
    'axes.grid': True,
    'grid.color': GRID,
    'grid.linewidth': 0.8,
    'axes.spines.top': False,
    'axes.spines.right': False,
    'lines.linewidth': 2,
})


def run_ids(raw, step):
    ids = set()
    for name in os.listdir(raw):
        for suffix in ('.env.txt', '.json', '.ramp.tsv'):
            if name.endswith(suffix):
                rid = name[:-len(suffix)]
                info = s.parse_run_id(rid)
                if info and (not step or info['step'] == step):
                    ids.add(rid)
    return sorted(ids)


def save(fig, path):
    fig.tight_layout()
    fig.savefig(path, metadata={'Date': None})
    plt.close(fig)
    print(path)


PLAIN = FuncFormatter(lambda v, _: f'{v:g}')


def threshold_line(ax):
    ax.axhline(s.RECOVERY_P95_MS, color=INK, linestyle='--', linewidth=1)
    ax.annotate('500 ms', (1, s.RECOVERY_P95_MS), xycoords=('axes fraction', 'data'),
                xytext=(-4, 4), textcoords='offset points', ha='right', color=INK)


def ramp_graph(step, runs, out):
    ramps = [r for r in runs if r['ramp']]
    if not ramps:
        return
    fig, ax = plt.subplots(figsize=(8, 4.5))
    for i, r in enumerate(ramps):
        xs = [float(row['rate']) for row in r['ramp']]
        ys = [float(row['first_p95_ms']) for row in r['ramp']]
        label = f'{r["env"].get("variant")} {r["env"].get("dataset")} (max stable {s.max_stable(r)})'
        ax.plot(xs, ys, marker='o', markersize=5, color=COLORS[i % len(COLORS)], label=label)
    threshold_line(ax)
    ax.set_xscale('log', base=2)
    ax.set_xticks(sorted({float(row['rate']) for r in ramps for row in r['ramp']}))
    ax.xaxis.set_major_formatter(PLAIN)
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.set_xlabel('target load (HTTP req/s)')
    ax.set_ylabel('first-page p95 (ms)')
    ax.set_title(f'{step} ramp', loc='left', color=INK)
    ax.legend(frameon=False)
    save(fig, os.path.join(out, f'{step}-ramp.svg'))


def steady_graph(step, runs, out):
    groups = {}
    for r in runs:
        p95 = s.metric(r, 'http_req_duration{kind:first}', 'p(95)')
        if r['env'].get('cell') == 'steady' and p95 is not None:
            groups.setdefault(f'{r["env"].get("variant")}\n{r["env"].get("dataset")}', []).append(p95)
    if not groups:
        return
    fig, ax = plt.subplots(figsize=(max(4, 1.6 * len(groups) + 2), 4.5))
    for i, (label, vals) in enumerate(groups.items()):
        color = COLORS[i % len(COLORS)]
        ax.vlines(i, min(vals), max(vals), color=color, linewidth=6)
        ax.plot([i] * len(vals), vals, 'o', color=color, markersize=5, markeredgecolor='white')
        med = statistics.median(vals)
        ax.plot([i - 0.2, i + 0.2], [med, med], color=INK, linewidth=2)
        ax.annotate(f'{med:.0f} ms', (i + 0.25, med), va='center', color=INK)
    ax.set_xticks(range(len(groups)), list(groups))
    ax.set_xlim(-0.6, len(groups) - 0.2)
    ax.set_ylim(bottom=0)
    ax.set_ylabel('first-page p95 (ms), runs and median')
    ax.set_title(f'{step} steady', loc='left', color=INK)
    save(fig, os.path.join(out, f'{step}-steady.svg'))


def rolling_p95(points, start, end, window):
    xs, ys = [], []
    t = start + 1
    while t <= end + 1:
        vals = [ms for ts, ms in points if t - window < ts <= t]
        if len(vals) >= s.RECOVERY_MIN_SAMPLES:
            xs.append(t)
            ys.append(s.p95(vals))
        t += 1
    return xs, ys


def spike_graph(run, pool, out):
    t0, samples = s.read_points(run['points'])
    kit = (run['summary'] or {}).get('kit', {})
    spike_seconds = kit.get('spike_seconds', 30)
    # 요약 표(spike_section)와 같은 회복 창과 회복 기준을 쓴다
    probe_rate = run['env'].get('probe_rate') or kit.get('probe_rate', 2)
    window = s.recovery_window(probe_rate)
    threshold = s.recovery_threshold(run, pool)
    fig, ax = plt.subplots(figsize=(9, 4.5))
    for i, scenario in enumerate(('burst', 'probe')):
        pts = [(ts - t0, ms) for ts, ms, sc, _ in samples if sc == scenario]
        if pts:
            ax.plot([p[0] for p in pts], [p[1] for p in pts], 'o', markersize=3, alpha=0.5,
                    color=COLORS[i], label=f'{scenario} request')
    probe = [(ts - t0, ms) for ts, ms, sc, _ in samples if sc == 'probe']
    if probe:
        xs, ys = rolling_p95(probe, spike_seconds, max(p[0] for p in probe), window)
        ax.plot(xs, ys, color=COLORS[2], label=f'probe rolling {window}s p95')
    ax.axvline(spike_seconds, color=INK, linewidth=1)
    rec = s.recovery_seconds(run['points'], spike_seconds, probe_rate, threshold, kit.get('probe_seconds', 90))
    if rec is not None:
        ax.axvline(spike_seconds + rec, color=COLORS[2], linewidth=1, linestyle=':')
    note = f'recovery {rec}s' if rec is not None else 'not recovered'
    ax.annotate(f'burst end, {note}', (spike_seconds, 0), xycoords=('data', 'axes fraction'),
                xytext=(4, 6), textcoords='offset points', color=INK)
    threshold_line(ax)
    if threshold != s.RECOVERY_P95_MS:
        ax.axhline(threshold, color=COLORS[2], linestyle='--', linewidth=1)
        ax.annotate(f'recovery {threshold:.0f} ms', (1, threshold), xycoords=('axes fraction', 'data'),
                    xytext=(-4, 4), textcoords='offset points', ha='right', color=COLORS[2])
    ax.set_yscale('log')
    ax.yaxis.set_major_locator(FixedLocator([10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 60000]))
    ax.yaxis.set_major_formatter(PLAIN)
    ax.yaxis.set_minor_formatter(NullFormatter())
    ax.set_xlabel('seconds since start')
    ax.set_ylabel('request duration (ms)')
    ax.set_title(run['id'], loc='left', color=INK, fontsize=9)
    ax.legend(frameon=False, loc='upper center', bbox_to_anchor=(0.5, -0.15), ncol=3)
    save(fig, os.path.join(out, f'{run["id"]}-spike.svg'))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--raw', default=s.DEFAULT_RAW)
    p.add_argument('--out', default=os.path.join(HERE, '..', 'results', 'graphs'))
    p.add_argument('--step')
    a = p.parse_args()
    os.makedirs(a.out, exist_ok=True)
    by_step = {}
    for rid in run_ids(a.raw, a.step):
        by_step.setdefault(s.parse_run_id(rid)['step'], []).append(s.load_run(a.raw, rid))
    if not by_step:
        sys.exit(f'no runs in {a.raw}')
    for step, runs in sorted(by_step.items()):
        ramp_graph(step, runs, a.out)
        steady_graph(step, runs, a.out)
        for r in runs:
            if r['env'].get('cell') == 'spike' and r['points']:
                spike_graph(r, runs, a.out)


if __name__ == '__main__':
    main()
