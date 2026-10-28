-- 구매자 주문 목록이 buyer_id로 거르고 id 역순 커서로 훑는 경로. 이름은 엔티티 @Table 선언과 맞춤.
-- local은 ddl-auto: update라 하이버네이트가 같은 이름으로 먼저 만들었을 수 있어 없을 때만 만듦.
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'order_groups'
          AND INDEX_NAME = 'idx_order_groups_buyer_id'
    ),
    'CREATE INDEX idx_order_groups_buyer_id ON order_groups (buyer_id, id)',
    'SELECT 1'
);
PREPARE idx_buyer_stmt FROM @stmt;
EXECUTE idx_buyer_stmt;
DEALLOCATE PREPARE idx_buyer_stmt;
