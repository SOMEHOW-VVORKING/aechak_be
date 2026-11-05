-- 키워드 상품 검색 인기순이 읽는 리뷰 수 비정규화 컬럼과 인기순, 낮은 가격순 정렬 인덱스.
-- products 테이블이 없으면 아무것도 하지 않는다. product_stats가 없으면 채우기만 건너뛴다.
SET @has_products = EXISTS(
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
);
SET @has_product_stats = EXISTS(
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_stats'
);

SET @stmt = IF(
    @has_products AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'products'
          AND COLUMN_NAME = 'review_count'
    ),
    'ALTER TABLE products ADD COLUMN review_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE products_review_count_stmt FROM @stmt;
EXECUTE products_review_count_stmt;
DEALLOCATE PREPARE products_review_count_stmt;

SET @stmt = IF(
    @has_products AND @has_product_stats,
    'UPDATE products p JOIN product_stats s ON s.product_id = p.id SET p.review_count = s.review_count',
    'SELECT 1'
);
PREPARE products_review_count_backfill_stmt FROM @stmt;
EXECUTE products_review_count_backfill_stmt;
DEALLOCATE PREPARE products_review_count_backfill_stmt;

SET @stmt = IF(
    @has_products AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'products'
          AND INDEX_NAME = 'ix_products_popular'
    ),
    'CREATE INDEX ix_products_popular ON products (review_count DESC, id DESC)',
    'SELECT 1'
);
PREPARE ix_products_popular_stmt FROM @stmt;
EXECUTE ix_products_popular_stmt;
DEALLOCATE PREPARE ix_products_popular_stmt;

SET @stmt = IF(
    @has_products AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'products'
          AND INDEX_NAME = 'ix_products_discount_price'
    ),
    'CREATE INDEX ix_products_discount_price ON products (discount_price, id DESC)',
    'SELECT 1'
);
PREPARE ix_products_discount_price_stmt FROM @stmt;
EXECUTE ix_products_discount_price_stmt;
DEALLOCATE PREPARE ix_products_discount_price_stmt;

SET @stmt = IF(
    @has_products AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'products'
          AND INDEX_NAME = 'ix_products_regular_price'
    ),
    'CREATE INDEX ix_products_regular_price ON products (regular_price, id DESC)',
    'SELECT 1'
);
PREPARE ix_products_regular_price_stmt FROM @stmt;
EXECUTE ix_products_regular_price_stmt;
DEALLOCATE PREPARE ix_products_regular_price_stmt;
