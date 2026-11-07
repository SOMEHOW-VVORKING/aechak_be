-- 검색 부하테스트용 대량 데이터. 셀러, 상품, 상품 통계를 비우고 같은 값으로 다시 만든다.
-- 전제: api가 Flyway와 ddl-auto=update로 한 번 부팅해 스키마와 카테고리 시드가 만들어진 상태.
-- 사용: 앞에서 SET @n_products := 100000; (또는 1000000, 10000000) 을 한 뒤 이 파일을 넣는다. @n_sellers 기본값은 1000.
-- 결정적이다. NOW()와 RAND()를 쓰지 않고 시각은 모두 절대값이라 다시 넣어도 manifest의 crc가 같다.
--
-- 상품명은 [한정판] 브랜드 대상 품목 [맛] 용량으로 조합해 검색어별 일치 비율이 고정된다(전체 상품 기준).
--   강아지 50%, 사료 10%, 연어 10%, 간식 6.7%, 관절 3.3%, 하네스 3.3%, 바잇미 2.5%, 한정판 0.1%, 없는상품 0%
-- 할인은 상품의 30%이고 기간은 2026-10-01 00:00:00부터 2027-06-30 23:59:59까지로 측정 시리즈 전체를 감싼다.
--
-- 경계 집합: n % 1000 이 503, 504, 505 인 상품(전체의 0.3%)은 description에 perf-boundary 라벨을 달고
-- 할인 기간을 아래처럼 둔다. 이름은 공식 그대로라 검색어 비율이 바뀌지 않는다. staleness 셀이 이 행만 골라 기간을 옮긴다.
--   perf-boundary:expired   2026-09-01 00:00:00 ~ 2026-09-30 23:59:59 (이미 끝난 할인, 유효가격은 정가)
--   perf-boundary:upcoming  2027-07-01 00:00:00 ~ 2027-12-31 23:59:59 (아직 시작 전, 유효가격은 정가)
--   perf-boundary:open      시작과 끝이 NULL (기간 제한 없는 할인, 유효가격은 할인가)

SET @n_products := COALESCE(@n_products, 100000);
SET @n_sellers := COALESCE(@n_sellers, 1000);
SET @base := TIMESTAMP '2026-10-01 00:00:00';
SET @discount_start := TIMESTAMP '2026-10-01 00:00:00';
SET @discount_end := TIMESTAMP '2027-06-30 23:59:59';

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE product_stats;
TRUNCATE TABLE products;
TRUNCATE TABLE sellers;
SET FOREIGN_KEY_CHECKS = 1;

DROP TABLE IF EXISTS perf_digits;
CREATE TABLE perf_digits (d INT PRIMARY KEY);
INSERT INTO perf_digits VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

DROP TABLE IF EXISTS perf_seq;
CREATE TABLE perf_seq (n INT PRIMARY KEY);
INSERT INTO perf_seq (n)
SELECT a.d + 10 * b.d + 100 * c.d + 1000 * d.d + 10000 * e.d + 100000 * f.d AS n
FROM perf_digits a, perf_digits b, perf_digits c, perf_digits d, perf_digits e, perf_digits f
HAVING n < GREATEST(@n_products, @n_sellers);

-- 100만 개를 넘는 크기는 위 번호에 백만 단위 블록을 더해 채운다(1,000만 건이면 블록 1에서 9)
DROP TABLE IF EXISTS perf_block;
CREATE TABLE perf_block (k INT PRIMARY KEY);
INSERT INTO perf_block (k)
SELECT x.d + 10 * y.d + 100 * z.d AS k
FROM perf_digits x, perf_digits y, perf_digits z
HAVING k BETWEEN 1 AND (@n_products - 1) DIV 1000000;
INSERT INTO perf_seq (n)
SELECT blk.k * 1000000 + s.n
FROM perf_block blk, perf_seq s
WHERE blk.k * 1000000 + s.n < @n_products;
DROP TABLE perf_block;

DROP TABLE IF EXISTS perf_leaf;
CREATE TABLE perf_leaf (rn INT PRIMARY KEY, id BIGINT NOT NULL);
INSERT INTO perf_leaf (rn, id)
SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id FROM categories WHERE depth = 3;
SET @n_leaf := (SELECT COUNT(*) FROM perf_leaf);

-- 셀러: 30%는 기본 배송비 0원(무료배송 필터 대상), 1%는 활성이 아니라 검색에서 빠진다.
-- 배송 정책 컬럼은 그 컬럼을 추가한 마이그레이션이 기존 행에 채운 값과 같다
INSERT INTO sellers (user_id, created_at, updated_at, base_shipping_fee, confirmed_order_count, free_shipping_threshold,
                     status, status_changed_at, store_name, telesales_notice_status, version,
                     return_same_as_origin, jeju_extra_fee, remote_area_extra_fee, return_fee, exchange_fee,
                     avg_dispatch_days)
SELECT n + 1, @base, @base,
       IF(n % 10 < 3, 0, 3000),
       0,
       IF(n % 10 >= 3 AND n % 2 = 0, 30000, NULL),
       IF(n % 100 = 99, 'SUSPENDED', 'ACTIVE'),
       @base,
       CONCAT('perf셀러', n + 1),
       'NOT_REQUIRED',
       0,
       b'1', 0, 0, 0, 0, 2
FROM perf_seq
WHERE n < @n_sellers;

-- 상품: 판매중 93%, 품절 5%, 판매중지 2%. 검수 대기 1%. 30%는 할인 중
INSERT INTO products (id, created_at, updated_at, description, discount_end_at, discount_price, discount_start_at,
                      inspection_status, name, public_id, regular_price, representative_image_key, sale_status,
                      seller_id, version, category_id)
SELECT n + 1,
       @base - INTERVAL (n % 180) DAY,
       @base - INTERVAL (n % 180) DAY,
       ELT(n % 1000 - 502, 'perf-boundary:expired', 'perf-boundary:upcoming', 'perf-boundary:open'),
       CASE n % 1000 WHEN 503 THEN TIMESTAMP '2026-09-30 23:59:59' WHEN 504 THEN TIMESTAMP '2027-12-31 23:59:59'
                     WHEN 505 THEN NULL ELSE IF(n % 10 < 3, @discount_end, NULL) END,
       IF(n % 10 < 3 OR n % 1000 BETWEEN 503 AND 505, FLOOR((5000 + ((n * 97) % 1450) * 100) * 0.8 / 100) * 100, NULL),
       CASE n % 1000 WHEN 503 THEN TIMESTAMP '2026-09-01 00:00:00' WHEN 504 THEN TIMESTAMP '2027-07-01 00:00:00'
                     WHEN 505 THEN NULL ELSE IF(n % 10 < 3, @discount_start, NULL) END,
       IF(n % 100 = 2, 'PENDING', 'APPROVED'),
       CONCAT_WS(' ',
                 IF(n % 1000 = 7, '한정판', NULL),
                 ELT(1 + (n * 17) % 40,
                     '로얄캐닌', '힐스', '오리젠', '나우', '아카나', '뉴트로', 'ANF', '내추럴발란스', '지위픽', '바잇미',
                     '하림펫푸드', '닥터독', '캣츠랑', '도그랑', '시저', '위스카스', '퓨리나', '이즈칸', '웰니스', '네츄럴코어',
                     '프로베스트', '테라카니스', '인섹트도그', '굿데이', '펫생각', '포켄스', '아이러브펫', '펫모닝', '코코브라운', '멍멍마켓',
                     '냥냥상점', '펫케어랩', '바른펫', '헬로도기', '헬로캣', '펫플러스', '포우즈', '펫토리', '해피테일', '올리펫'),
                 ELT(1 + (n * 31) % 10, '강아지', '강아지', '강아지', '강아지', '강아지', '고양이', '고양이', '고양이', '고양이', '반려동물'),
                 ELT(1 + (n * 7) % 30,
                     '건식사료', '습식사료', '처방사료', '동결건조 간식', '저키 간식', '덴탈껌', '츄르', '관절 영양제', '유산균', '오메가3',
                     '하네스', '리드줄', '목줄', '방석', '쿠션', '이동장', '장난감', '터널', '스크래쳐', '샴푸',
                     '배변패드', '모래', '급식기', '급수기', '칫솔', '발톱깎이', '빗', '옷', '우비', '캣타워'),
                 ELT(1 + (n * 13) % 10, '연어', '닭고기', '소고기', '오리', '양고기', '참치', '칠면조', NULL, NULL, NULL),
                 ELT(1 + (n * 3) % 6, '1kg', '2kg', '6kg', '300g', 'S', 'M')),
       CONCAT('01PERF', LPAD(n + 1, 20, '0')),
       5000 + ((n * 97) % 1450) * 100,
       CONCAT('products/perf/', n + 1, '.jpg'),
       CASE WHEN n % 20 = 0 THEN 'OUT_OF_STOCK' WHEN n % 50 = 1 THEN 'SUSPENDED' ELSE 'ON_SALE' END,
       1 + (n * 37) % @n_sellers,
       0,
       (SELECT id FROM perf_leaf WHERE rn = (n * 11) % @n_leaf)
FROM perf_seq
WHERE n < @n_products;

-- 상품 통계: 60%만 행이 있다. 리뷰 수는 소수 상품에 몰리게 3제곱으로 치우친다.
-- 부동소수 POW 대신 정수 연산이라 플랫폼이 달라도 같은 값이 나온다
INSERT INTO product_stats (product_id, created_at, updated_at, average_rating, like_count, rating_sum, review_count)
SELECT n + 1, @base, @base,
       3.00 + ((n * 13) % 21) / 10,
       (n * 31) % 1000,
       ROUND((3.00 + ((n * 13) % 21) / 10) * ((((n * 7919) % 1000) * ((n * 7919) % 1000) * ((n * 7919) % 1000) * 500) DIV 1000000000)),
       (((n * 7919) % 1000) * ((n * 7919) % 1000) * ((n * 7919) % 1000) * 500) DIV 1000000000
FROM perf_seq
WHERE n < @n_products AND n % 5 < 3;

DROP TABLE perf_leaf;
DROP TABLE perf_seq;
DROP TABLE perf_digits;

ANALYZE TABLE sellers, products, product_stats;

SELECT (SELECT COUNT(*) FROM sellers) AS sellers, (SELECT COUNT(*) FROM products) AS products,
       (SELECT COUNT(*) FROM product_stats) AS product_stats;
