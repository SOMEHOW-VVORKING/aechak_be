-- 검색 인기순 정렬용 리뷰 수 비정규화 컬럼과 검색 정렬 인덱스. add_products_search_sort_indexes 마이그레이션과 같은 문장
ALTER TABLE products ADD COLUMN review_count INT NOT NULL DEFAULT 0;
UPDATE products p JOIN product_stats s ON s.product_id = p.id SET p.review_count = s.review_count;
CREATE INDEX ix_products_popular ON products (review_count DESC, id DESC);
CREATE INDEX ix_products_discount_price ON products (discount_price, id DESC);
CREATE INDEX ix_products_regular_price ON products (regular_price, id DESC);
