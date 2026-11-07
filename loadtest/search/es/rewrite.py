"""ES 호스트에서 products 색인의 문서를 같은 _source 그대로 초당 RATE건 다시 색인한다(es/perturb.sh write).

python3 -I rewrite.py --start-ms MS --end-ms MS --rate R [--pool 1000] [--es http://localhost:9200]
문서 POOL개를 먼저 읽어 두고 START_MS부터 END_MS까지 1 / R초마다 한 건씩 돌아가며 _bulk index로 보낸다(밀리면 묶어서).
같은 _id에 같은 내용이라 검색 결과는 그대로이고 버전, 새 세그먼트, refresh, 요청 캐시 무효화는 실제 쓰기처럼 생긴다.
부분 _update는 같은 값이면 noop이라 아무것도 쓰지 않아서 쓰지 않는다.
PERTURB_START, PERTURB_END, PERTURB_RESULT 줄을 표준 출력에 찍는다.
"""
import argparse
import http.client
import json
import sys
import time
from urllib.parse import urlparse

INDEX = 'products'


def now_ms():
    return int(time.time() * 1000)


def request(conn, method, path, body=None, content_type='application/json'):
    conn.request(method, path, body=body, headers={'Content-Type': content_type} if body is not None else {})
    res = conn.getresponse()
    data = res.read()
    return res.status, json.loads(data) if data else None


def load_pool(conn, size):
    status, body = request(conn, 'POST', f'/{INDEX}/_search',
                           json.dumps({'size': size, 'query': {'match_all': {}}, 'sort': ['_doc']}))
    if status != 200:
        sys.exit(f'문서를 읽지 못했다: HTTP {status} {body}')
    hits = [(h['_id'], h['_source']) for h in body['hits']['hits']]
    if not hits:
        sys.exit('products 색인에 문서가 없다')
    return hits


def bulk_line(doc_id, source):
    action = json.dumps({'index': {'_index': INDEX, '_id': doc_id}})
    return (action + '\n' + json.dumps(source, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--start-ms', type=int, required=True)
    ap.add_argument('--end-ms', type=int, required=True)
    ap.add_argument('--rate', type=float, required=True)
    ap.add_argument('--pool', type=int, default=1000)
    ap.add_argument('--es', default='http://localhost:9200')
    a = ap.parse_args()
    if a.rate <= 0 or a.end_ms <= a.start_ms or not 1 <= a.pool <= 10000:
        sys.exit('rate > 0, end > start, 1 <= pool <= 10000')
    u = urlparse(a.es)
    conn = http.client.HTTPConnection(u.hostname, u.port or 9200, timeout=10)
    pool = load_pool(conn, a.pool)
    results = {}
    errors = sent = calls = 0
    time.sleep(max(0.0, a.start_ms / 1000 - time.time()))
    start = now_ms()
    print(f'PERTURB_START {start}', flush=True)
    # 시작부터 지금까지 보냈어야 할 수만큼 보낸다. 호출이 간격보다 길어지면 밀린 문서를 한 _bulk에 묶는다
    total = max(1, int((a.end_ms - start) / 1000 * a.rate))
    while sent < total:
        due = min(int((time.time() * 1000 - start) / 1000 * a.rate) + 1, total)
        if due <= sent:
            time.sleep(max(0.0, start / 1000 + sent / a.rate - time.time()))
            continue
        batch = [pool[i % len(pool)] for i in range(sent, due)]
        sent = due
        calls += 1
        try:
            status, body = request(conn, 'POST', '/_bulk', b''.join(bulk_line(d, src) for d, src in batch), 'application/x-ndjson')
            body = body or {}
            if status != 200 or body.get('errors'):
                errors += 1
            for item in body.get('items', []):
                r = item.get('index', {}).get('result', 'error')
                results[r] = results.get(r, 0) + 1
        except (OSError, http.client.HTTPException, ValueError):
            errors += 1
            conn.close()
            conn = http.client.HTTPConnection(u.hostname, u.port or 9200, timeout=10)
    end = now_ms()
    print(f'PERTURB_END {end}', flush=True)
    rate = sent / ((end - start) / 1000) if end > start else 0
    parts = ' '.join(f'{k}={v}' for k, v in sorted(results.items()))
    print(f'PERTURB_RESULT sent={sent} calls={calls} errors={errors} rate={rate:.2f} pool={len(pool)} {parts}'.rstrip(), flush=True)


if __name__ == '__main__':
    main()
