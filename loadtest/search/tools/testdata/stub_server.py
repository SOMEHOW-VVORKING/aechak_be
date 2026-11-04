"""검색 API 흉내 서버. search.js, equiv.js, summarize.py를 실제 api 없이 확인할 때 쓴다.

python3 -I stub_server.py [--port 18999] [--errors] [--all-errors] [--slow-seconds N] [--rw-log FILE]
  --errors        검색어 한정판에 503(errorCode 90000)을 돌려준다
  --all-errors    모든 검색에 503(errorCode 90000)을 돌려준다(안전 중단 확인용)
  --slow-seconds  첫 요청부터 N초 동안 응답을 700ms로 늦춘다(spike 회복 확인용)
  --rw-log        POST /api/v1/write(Prometheus remote write)로 받은 k6 지표 이름을 파일에 적는다
"""
import argparse
import hashlib
import json
import random
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PAGE_SIZE = 20
LAST_PAGE = 3


def snappy_decode(buf):
    """snappy 블록 형식 해제. remote write 본문 확인용."""
    n = shift = pos = 0
    while True:
        b = buf[pos]
        pos += 1
        n |= (b & 0x7F) << shift
        shift += 7
        if b < 0x80:
            break
    out = bytearray()
    while pos < len(buf):
        tag = buf[pos]
        pos += 1
        kind = tag & 3
        if kind == 0:
            length = tag >> 2
            if length >= 60:
                extra = length - 59
                length = int.from_bytes(buf[pos:pos + extra], 'little')
                pos += extra
            length += 1
            out += buf[pos:pos + length]
            pos += length
            continue
        if kind == 1:
            length = ((tag >> 2) & 7) + 4
            offset = ((tag >> 5) << 8) | buf[pos]
            pos += 1
        elif kind == 2:
            length = (tag >> 2) + 1
            offset = int.from_bytes(buf[pos:pos + 2], 'little')
            pos += 2
        else:
            length = (tag >> 2) + 1
            offset = int.from_bytes(buf[pos:pos + 4], 'little')
            pos += 4
        for _ in range(length):
            out.append(out[-offset])
    assert len(out) == n, (len(out), n)
    return bytes(out)


class Handler(BaseHTTPRequestHandler):
    opts = None
    started = None
    lock = threading.Lock()
    rw_names = set()

    def log_message(self, *args):
        pass

    def send_json(self, status, body):
        raw = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        if self.path != '/api/v1/write':
            return self.send_json(404, {'errorCode': 90006, 'message': 'not found'})
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        raw = snappy_decode(body)
        names = set(m.decode() for m in re.findall(rb'k6_[a-z0-9_]+', raw))
        names |= {'label:' + k for k in ('testid', 'kind', 'phase', 'kw_class', 'status', 'code', 'scenario') if k.encode() in raw}
        with Handler.lock:
            Handler.rw_names |= names
            if self.opts.rw_log:
                with open(self.opts.rw_log, 'w') as f:
                    f.write('\n'.join(sorted(Handler.rw_names)) + '\n')
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != '/api/v1/search/products':
            return self.send_json(404, {'errorCode': 90006, 'message': 'not found'})
        with Handler.lock:
            if Handler.started is None:
                Handler.started = time.time()
        slow = time.time() - Handler.started < self.opts.slow_seconds
        time.sleep(0.7 if slow else random.uniform(0.010, 0.030))
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        kw = q.get('keyword', '')
        if not kw.strip():
            return self.send_json(400, {'errorCode': 90001, 'message': '잘못된 요청입니다.'})
        if self.opts.all_errors or (self.opts.errors and kw == '한정판'):
            return self.send_json(503, {'errorCode': 90000, 'message': '서버 오류가 발생했습니다.'})
        cursor = q.get('cursor')
        page = int(cursor[1:]) if cursor and cursor.startswith('p') else 1
        if kw == '없는상품':
            return self.send_json(200, {'data': {'products': [], 'totalCount': 0, 'nextCursor': None, 'hasNext': False}})
        key = '|'.join([kw, q.get('sort', 'popular'), q.get('minPrice', ''), q.get('category', ''), q.get('freeShipping', '')])
        seed = hashlib.sha256(key.encode()).hexdigest()[:8]
        products = [{'productId': f'{seed}-{page}-{i:02d}', 'name': kw} for i in range(PAGE_SIZE)]
        has_next = page < LAST_PAGE
        self.send_json(200, {'data': {
            'products': products,
            'totalCount': LAST_PAGE * PAGE_SIZE if page == 1 else None,
            'nextCursor': f'p{page + 1}' if has_next else None,
            'hasNext': has_next,
        }})


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--port', type=int, default=18999)
    p.add_argument('--errors', action='store_true')
    p.add_argument('--all-errors', action='store_true')
    p.add_argument('--slow-seconds', type=float, default=0)
    p.add_argument('--rw-log')
    Handler.opts = p.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', Handler.opts.port), Handler)
    server.daemon_threads = True
    server.serve_forever()


if __name__ == '__main__':
    main()
