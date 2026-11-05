-- 어드민 판매중지: products에 "운영자가 판매를 막았다"는 표시와 그 맥락을 붙인다.
--
-- 표시를 sale_status와 따로 두는 이유: SaleStatus.isSellerAssignable()이 SUSPENDED를 셀러에게
-- 허용하므로, 상태만 옮기면 셀러가 자기 상품 상태 변경 경로로 즉시 ON_SALE로 되돌린다. 이 표시가
-- 있는 동안 셀러의 상태 변경을 거부하고, 운영자가 재개하면 표시를 지운다.
--
-- 검수 상태(inspection_status)와도 가르지 않는다 — 검수 반려는 이미 노출을 끊는 별 축이고,
-- 이 표시는 검수와 무관한 이유로 판매를 막는 축이다. 한 칸에 합치면 둘 중 하나를 풀 때
-- 다른 하나의 판단까지 함께 풀린다.
--
-- 조건부 ALTER인 이유 둘.
--  1) dev는 Flyway와 ddl-auto(update)가 함께 돌아 엔티티 선언을 본 Hibernate가 같은 컬럼을 먼저
--     만들어 둔 DB가 있을 수 있다. 맨 ALTER는 그 DB에서 중복 컬럼으로 실패하고, 한 번 실패한
--     파일은 그 뒤로 영구 미적용으로 남는다(V55와 같은 수렴 방식).
--  2) products는 Flyway가 만드는 테이블이 아니다(아직 ddl-auto 소유). 빈 DB에 전체 적용하는
--     경로에서는 이 시점에 테이블이 아예 없으므로 테이블 조건도 함께 본다(V22와 같은 방식).

SET @saved_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 5;

-- 1. 막은 시각. 이 한 칸이 "지금 운영자가 막았는가"의 축이다 — 재개하면 다시 NULL이 된다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'admin_sale_blocked_at'
    ),
    'ALTER TABLE products ADD COLUMN admin_sale_blocked_at DATETIME(6) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_blocked_at FROM @stmt;
EXECUTE add_blocked_at;
DEALLOCATE PREPARE add_blocked_at;

-- 2. 막은 사유. 셀러에게 알릴 문장이 아니라 운영 기록이다 — 분쟁에서 "왜 막았나"를 답하는 칸
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'admin_sale_block_reason'
    ),
    'ALTER TABLE products ADD COLUMN admin_sale_block_reason VARCHAR(500) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_block_reason FROM @stmt;
EXECUTE add_block_reason;
DEALLOCATE PREPARE add_block_reason;

-- 3. 막은 운영자. 값 참조(users.id)이고 FK는 두지 않는다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'admin_sale_blocked_by_admin_id'
    ),
    'ALTER TABLE products ADD COLUMN admin_sale_blocked_by_admin_id BIGINT NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_blocked_by FROM @stmt;
EXECUTE add_blocked_by;
DEALLOCATE PREPARE add_blocked_by;

-- 4. 막기 직전의 판매 상태. 재개가 되돌릴 자리다 — 일괄로 ON_SALE로 되돌리면 스스로 판매중지해 둔
--    셀러의 의사를 운영자 조치가 뒤집는다(sellers.previous_status와 같은 결)
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'admin_sale_block_previous_status'
    ),
    'ALTER TABLE products ADD COLUMN admin_sale_block_previous_status VARCHAR(30) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_block_previous_status FROM @stmt;
EXECUTE add_block_previous_status;
DEALLOCATE PREPARE add_block_previous_status;

SET SESSION lock_wait_timeout = @saved_lock_wait_timeout;
