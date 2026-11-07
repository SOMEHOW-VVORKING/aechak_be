"""seed.sql의 상품명 어휘에서 일치 비율을 아는 검색어 목록을 만든다(SEARCH-T14 다양한 검색어 구성).

python3 -I tools/gen_keywords.py [--count 300] [--out keywords-diverse.js]
상품명은 seed.sql products INSERT의 CONCAT_WS 안 IF(한정판)와 ELT 다섯 개로 정해지고 3000건마다 같은 이름이 돌아온다.
한 주기의 이름을 모두 만들어 어휘 단어의 2글자 이상 한글 부분 문자열마다 이름에 들어 있는 비율을 센다.
일치하는 이름이 없는 none 검색어는 어휘 부분 문자열을 뒤집은 것 중 어떤 이름에도 없는 것으로 만든다.
결과는 search.js가 KEYWORD_SET=diverse일 때 읽는 ES 모듈이다. 같은 seed.sql이면 늘 같은 파일이 나온다.
"""
import argparse
import json
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, '..', 'seed.sql')
DEFAULT_OUT = os.path.join(HERE, '..', 'keywords-diverse.js')
HANGUL = re.compile(r'^[가-힣]+$')
# 등급 경계(전체 상품 중 일치 비율). README의 고정 검색어 9개 등급과 같은 구간이다
CLASS_BOUNDS = [('high', 0.3), ('mid', 0.05), ('low', 0.01), ('rare', 0.0)]
# 고정 검색어 9개의 비율. seed.sql을 제대로 읽었는지 확인하는 데 쓴다
FIXED = {'강아지': 0.5, '사료': 0.1, '연어': 0.1, '간식': 1 / 15, '관절': 1 / 30, '하네스': 1 / 30,
         '바잇미': 0.025, '한정판': 0.001, '없는상품': 0.0}


def sql_list(text):
    """ELT 인자 목록에서 문자열과 NULL을 차례로 읽는다."""
    return [None if m.group(1) is None else m.group(1) for m in re.finditer(r"'([^']*)'|NULL", text)]


def parse_seed(path):
    """(한정판 주기, 한정판 나머지, 한정판 단어, [(곱, 법, 값 목록)]) 를 돌려준다."""
    with open(path, encoding='utf-8') as f:
        sql = f.read()
    m = re.search(r'CONCAT_WS\(\' \',(.*?)\),\s*\n\s*CONCAT\(\'01PERF\'', sql, re.S)
    assert m, 'seed.sql에서 상품명 CONCAT_WS를 찾지 못했다'
    body = m.group(1)
    lim = re.search(r"IF\(n % (\d+) = (\d+), '([^']+)', NULL\)", body)
    assert lim, 'seed.sql에서 한정판 조건을 찾지 못했다'
    parts = []
    for e in re.finditer(r'ELT\(1 \+ \(n \* (\d+)\) % (\d+),(.*?)\)(?=,\s*\n|\s*$)', body, re.S):
        values = sql_list(e.group(3))
        assert len(values) == int(e.group(2)), f'ELT 인자 수가 법과 다르다: {e.group(2)} {len(values)}'
        parts.append((int(e.group(1)), int(e.group(2)), values))
    assert len(parts) == 5, f'ELT가 5개가 아니다: {len(parts)}'
    return int(lim.group(1)), int(lim.group(2)), lim.group(3), parts


def names(seed):
    """이름이 되풀이되는 한 주기의 상품명 목록."""
    lim_mod, lim_rem, lim_word, parts = seed
    period = lim_mod
    for _, mod, _ in parts:
        period = period * mod // math.gcd(period, mod)
    out = []
    for n in range(period):
        words = [lim_word] if n % lim_mod == lim_rem else []
        words += [vals[(n * mul) % mod] for mul, mod, vals in parts]
        out.append(' '.join(w for w in words if w is not None).lower())
    return out


def vocabulary(seed):
    lim_word, parts = seed[2], seed[3]
    words = {lim_word}
    for _, _, vals in parts:
        for v in vals:
            if v:
                words.update(v.split(' '))
    return sorted(words)


def substrings(word):
    return {word[i:j] for i in range(len(word)) for j in range(i + 2, len(word) + 1)}


def cls_of(ratio):
    if ratio == 0:
        return 'none'
    return next(c for c, lo in CLASS_BOUNDS if ratio >= lo)


def generate(seed, count):
    pool = names(seed)
    total = len(pool)

    def ratio(kw):
        return sum(1 for name in pool if kw in name) / total

    hangul = sorted(s for w in vocabulary(seed) for s in substrings(w) if HANGUL.match(s))
    matched = sorted({s: ratio(s) for s in hangul}.items(), key=lambda kv: (-kv[1], kv[0]))
    reversed_ = sorted({s[::-1] for s, _ in matched} - {s for s, _ in matched})
    none = [s for s in reversed_ if ratio(s) == 0]
    for kw, want in FIXED.items():
        assert abs(ratio(kw) - want) < 1e-9, f'{kw} 비율 {ratio(kw)}가 README 값 {want}와 다르다'
    # 일치 검색어를 비율 순으로 고르게 솎고 none은 전체의 5% 남짓만 넣는다
    n_none = min(len(none), max(1, count // 20))
    n_match = min(len(matched), count - n_none)
    step = len(matched) / n_match
    picked = [matched[int(i * step)] for i in range(n_match)]
    picked += [(s, 0.0) for s in none[:: max(1, len(none) // n_none)][:n_none]]
    return total, [{'kw': kw, 'w': 1, 'cls': cls_of(r), 'ratio': round(r, 6)} for kw, r in picked]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--count', type=int, default=300)
    ap.add_argument('--out', default=DEFAULT_OUT)
    ap.add_argument('--seed', default=SEED)
    a = ap.parse_args()
    period, rows = generate(parse_seed(a.seed), a.count)
    assert len({r['kw'] for r in rows}) == len(rows), '검색어가 겹친다'
    by_cls = {}
    for r in rows:
        by_cls[r['cls']] = by_cls.get(r['cls'], 0) + 1
    head = [
        '// tools/gen_keywords.py가 seed.sql 상품명 어휘에서 만든 파일이다. 손으로 고치지 않고 다시 만든다.',
        f'// 검색어 {len(rows)}개, 등급별 {json.dumps(by_cls, ensure_ascii=False)}. ratio는 이름 {period}개 주기에서 센 전체 상품 중 일치 비율이다.',
        '// w는 선택 가중치이고 모두 1이라 검색어마다 고르게 고른다.',
    ]
    body = ',\n'.join('  ' + json.dumps(r, ensure_ascii=False) for r in rows)
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(head) + '\nexport default [\n' + body + ',\n]\n')
    print(f'{a.out}: {len(rows)}개 {by_cls}')


if __name__ == '__main__':
    main()
