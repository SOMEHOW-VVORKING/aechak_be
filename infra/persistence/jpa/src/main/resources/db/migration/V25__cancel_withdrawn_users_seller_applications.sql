-- 탈퇴한 사용자의 승인 전 입점 신청서(APPROVED, CANCELLED 제외)를 CANCELLED로 바꾸고 version을 올린다.
-- seller_applications는 Flyway 관리 밖이라 테이블이 없는 DB에서는 건너뛴다.

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
    ),
    'UPDATE seller_applications sa JOIN users u ON u.id = sa.user_id
     SET sa.status = ''CANCELLED'', sa.version = sa.version + 1, sa.updated_at = NOW(6)
     WHERE u.status = ''WITHDRAWN'' AND sa.status NOT IN (''APPROVED'', ''CANCELLED'')',
    'SELECT 1'
);
PREPARE cancel_withdrawn_applications_stmt FROM @stmt;
EXECUTE cancel_withdrawn_applications_stmt;
DEALLOCATE PREPARE cancel_withdrawn_applications_stmt;
