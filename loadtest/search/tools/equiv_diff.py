"""equiv.js 결과 두 개를 비교해 한국어 markdown 표를 출력한다.

python3 -I tools/equiv_diff.py A.jsonl B.jsonl
혼합 검색어(search.js의 9개) 행이 하나라도 다르면 종료 코드 1, 경계 사례만 다르면 0이다.
"""
import json
import os
import sys
import unicodedata

MIXED = {'강아지', '사료', '연어', '간식', '관절', '하네스', '바잇미', '한정판', '없는상품'}
FIELDS = ('status', 'totalCount', 'hasNext')


def load(path):
    rows = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line.startswith('EQUIV '):
                line = line[len('EQUIV '):]
            if not line:
                continue
            r = json.loads(line)
            rows[(r['kw'], r['sort'], r['filter'], r['page'])] = r
    return rows


def label(kw):
    if unicodedata.normalize('NFC', kw) != kw:
        return f'{unicodedata.normalize("NFC", kw)}(NFD)'
    if len(kw) > 12:
        return f'{kw[:5]}...({len(kw)}자)'
    return kw


def describe(a, b):
    if a is None:
        return 'A에 없음'
    if b is None:
        return 'B에 없음'
    parts = [f'{k} {a.get(k)} → {b.get(k)}' for k in FIELDS if a.get(k) != b.get(k)]
    ia, ib = a['ids'], b['ids']
    if ia != ib:
        if set(ia) == set(ib):
            parts.append('순서만 다름')
        else:
            parts.append(f'id 다름(A에만 {len(set(ia) - set(ib))}, B에만 {len(set(ib) - set(ia))})')
    return ', '.join(parts)


def diff(a_rows, b_rows):
    """(mixed 여부, key, 설명) 목록."""
    out = []
    for key in sorted(set(a_rows) | set(b_rows), key=lambda k: (k[0] not in MIXED, k)):
        d = describe(a_rows.get(key), b_rows.get(key))
        if d:
            out.append((key[0] in MIXED, key, d))
    return out


def render(a_path, b_path, rows, total):
    lines = [f'equiv 비교: `{os.path.basename(a_path)}` 대 `{os.path.basename(b_path)}` (비교 행 {total}개, 다른 행 {len(rows)}개)', '']
    if not rows:
        lines.append('차이 없음.')
        return '\n'.join(lines)
    lines += ['| 구분 | 검색어 | 정렬 | 필터 | 페이지 | 차이 |', '|---|---|---|---|---|---|']
    for mixed, (kw, sort, flt, page), d in rows:
        lines.append(f'| {"혼합" if mixed else "경계"} | {label(kw)} | {sort} | {flt} | {page} | {d} |')
    return '\n'.join(lines)


def self_test():
    base = {'kw': '강아지', 'sort': 'popular', 'filter': 'none', 'page': 1, 'status': 200,
            'totalCount': 60, 'ids': ['a', 'b'], 'hasNext': True}
    a = {('강아지', 'popular', 'none', 1): base, ('%', 'popular', 'none', 1): dict(base, kw='%')}
    assert diff(a, dict(a)) == []
    b = dict(a)
    b[('%', 'popular', 'none', 1)] = dict(base, kw='%', ids=['b', 'a'])
    assert [r[0] for r in diff(a, b)] == [False] and diff(a, b)[0][2] == '순서만 다름'
    b[('강아지', 'popular', 'none', 1)] = dict(base, totalCount=59, ids=['a', 'c'])
    r = diff(a, b)
    assert r[0][0] is True and r[0][2] == 'totalCount 60 → 59, id 다름(A에만 1, B에만 1)', r
    assert label('강아지') == '강아지(NFD)'
    print('self-test ok')


def main(argv):
    if argv[1:] == ['--self-test']:
        return self_test()
    if len(argv) != 3:
        sys.exit('usage: equiv_diff.py A.jsonl B.jsonl')
    a_rows, b_rows = load(argv[1]), load(argv[2])
    rows = diff(a_rows, b_rows)
    print(render(argv[1], argv[2], rows, len(set(a_rows) | set(b_rows))))
    sys.exit(1 if any(mixed for mixed, _, _ in rows) else 0)


if __name__ == '__main__':
    main(sys.argv)
