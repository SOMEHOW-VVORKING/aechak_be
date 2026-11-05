-- 시드 상태의 지문을 key<TAB>value 행으로 찍는다. mysql -N -B --default-character-set=utf8mb4 로 실행한다.
-- crc는 시드가 채우는 컬럼만 묶어서 나중에 컬럼이나 인덱스를 더해도 값이 바뀌지 않는다.
-- visible_products와 keyword.*는 검색 API와 같은 노출 규칙(검수 승인, 판매중이나 품절, 활성 셀러, 카테고리 체인 활성)을 쓴다.
-- 그래서 keyword.* 는 필터 없는 검색의 totalCount와 같다.

SELECT 'rows.products', COUNT(*) FROM products
UNION ALL SELECT 'rows.product_stats', COUNT(*) FROM product_stats
UNION ALL SELECT 'rows.sellers', COUNT(*) FROM sellers
UNION ALL SELECT 'rows.categories', COUNT(*) FROM categories
UNION ALL
SELECT 'crc.products',
       BIT_XOR(CRC32(CONCAT_WS('#', id, created_at, updated_at, IFNULL(description, 'NULL'),
                               IFNULL(discount_end_at, 'NULL'), IFNULL(discount_price, 'NULL'),
                               IFNULL(discount_start_at, 'NULL'), inspection_status, name, public_id, regular_price,
                               IFNULL(representative_image_key, 'NULL'), sale_status, seller_id, version, category_id)))
FROM products
UNION ALL
SELECT 'crc.product_stats',
       BIT_XOR(CRC32(CONCAT_WS('#', product_id, created_at, updated_at, IFNULL(average_rating, 'NULL'), like_count,
                               rating_sum, review_count)))
FROM product_stats
UNION ALL
SELECT 'crc.sellers',
       BIT_XOR(CRC32(CONCAT_WS('#', user_id, created_at, updated_at, base_shipping_fee, confirmed_order_count,
                               IFNULL(free_shipping_threshold, 'NULL'), status, status_changed_at, store_name,
                               telesales_notice_status, version, return_same_as_origin + 0, jeju_extra_fee,
                               remote_area_extra_fee, return_fee, exchange_fee, avg_dispatch_days)))
FROM sellers
UNION ALL
SELECT 'active_discounts', COUNT(*)
FROM products
WHERE discount_price IS NOT NULL
  AND (discount_start_at IS NULL OR discount_start_at <= NOW())
  AND (discount_end_at IS NULL OR discount_end_at >= NOW());

WITH v AS (SELECT p.name
           FROM products p
                    JOIN sellers s ON s.user_id = p.seller_id AND s.status = 'ACTIVE'
                    JOIN categories c ON c.id = p.category_id
                    LEFT JOIN categories pc ON pc.id = c.parent_id
                    LEFT JOIN categories gc ON gc.id = pc.parent_id
           WHERE p.inspection_status = 'APPROVED'
             AND p.sale_status IN ('ON_SALE', 'OUT_OF_STOCK')
             AND c.status = 'ACTIVE'
             AND (pc.id IS NULL OR pc.status = 'ACTIVE')
             AND (gc.id IS NULL OR gc.status = 'ACTIVE'))
SELECT 'visible_products', COUNT(*) FROM v
UNION ALL SELECT 'keyword.강아지', COUNT(*) FROM v WHERE LOWER(name) LIKE '%강아지%'
UNION ALL SELECT 'keyword.사료', COUNT(*) FROM v WHERE LOWER(name) LIKE '%사료%'
UNION ALL SELECT 'keyword.연어', COUNT(*) FROM v WHERE LOWER(name) LIKE '%연어%'
UNION ALL SELECT 'keyword.간식', COUNT(*) FROM v WHERE LOWER(name) LIKE '%간식%'
UNION ALL SELECT 'keyword.관절', COUNT(*) FROM v WHERE LOWER(name) LIKE '%관절%'
UNION ALL SELECT 'keyword.하네스', COUNT(*) FROM v WHERE LOWER(name) LIKE '%하네스%'
UNION ALL SELECT 'keyword.바잇미', COUNT(*) FROM v WHERE LOWER(name) LIKE '%바잇미%'
UNION ALL SELECT 'keyword.한정판', COUNT(*) FROM v WHERE LOWER(name) LIKE '%한정판%'
UNION ALL SELECT 'keyword.없는상품', COUNT(*) FROM v WHERE LOWER(name) LIKE '%없는상품%';
