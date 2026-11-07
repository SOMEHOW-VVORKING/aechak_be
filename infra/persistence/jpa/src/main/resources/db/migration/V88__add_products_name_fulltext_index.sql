-- 키워드 상품 검색의 상품명 MATCH AGAINST가 쓰는 ngram FULLTEXT 인덱스.
-- products 테이블이 있고 인덱스가 없을 때만 만든다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'products'
          AND INDEX_NAME = 'ft_products_name'
    ),
    'ALTER TABLE products ADD FULLTEXT INDEX ft_products_name (name) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE ft_products_name_stmt FROM @stmt;
EXECUTE ft_products_name_stmt;
DEALLOCATE PREPARE ft_products_name_stmt;
