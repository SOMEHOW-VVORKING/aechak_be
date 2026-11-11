-- 노출 게이트가 꺼져 있던 동안 위반 판정을 받고도 APPROVED로 남은 상품을 REJECTED로 옮긴다.
-- 대상은 열린 검토 행(PENDING_REVIEW)이 있고 그 행을 막고 있는 판정이 FLAG인 상품이다.
-- 막고 있는 판정은 행의 blinded_at과 판정 시각이 같은 검수 중 가장 나중 것이다.
-- 이미 REJECTED인 상품은 건드리지 않아 다시 실행해도 결과가 같다.
-- products는 Flyway 관리 밖이라 테이블이 없는 DB에서는 건너뛴다.

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ),
    'UPDATE products p
         JOIN product_review_queue_items q
         ON q.target_type = ''PRODUCT'' AND q.target_id = p.id AND q.status = ''PENDING_REVIEW''
     SET p.inspection_status = ''REJECTED'', p.version = p.version + 1, p.updated_at = NOW(6)
     WHERE p.inspection_status <> ''REJECTED''
       AND (SELECT ci.judgment
            FROM content_inspections ci
            WHERE ci.target_type = ''PRODUCT'' AND ci.target_ref = p.id AND ci.settled_at = q.blinded_at
            ORDER BY ci.id DESC
            LIMIT 1) = ''FLAG''',
    'SELECT 1'
);
PREPARE reject_flagged_products_stmt FROM @stmt;
EXECUTE reject_flagged_products_stmt;
DEALLOCATE PREPARE reject_flagged_products_stmt;
