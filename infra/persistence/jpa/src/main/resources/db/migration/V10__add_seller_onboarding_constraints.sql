-- SLR-01 셀러 입점 착수 전 스키마 보강. 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 맞춘다.
-- 대상 테이블은 아직 Flyway 관리 밖(ddl-auto 생성)이라, 테이블이 없는 새 DB에서는 건너뛴다 —
-- 그 경우 ddl-auto가 엔티티 선언대로 처음부터 만든다(V7 선례).

-- 유저당 신청 1행(행 재사용 모델) 불변식의 DB 방어선. 동시 INSERT race는 이 UNIQUE가 최후로 막는다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'user_id'
          AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE seller_applications MODIFY COLUMN user_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE not_null_stmt FROM @stmt;
EXECUTE not_null_stmt;
DEALLOCATE PREPARE not_null_stmt;

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND INDEX_NAME = 'uk_seller_applications_user_id'
    ),
    'ALTER TABLE seller_applications ADD CONSTRAINT uk_seller_applications_user_id UNIQUE (user_id)',
    'SELECT 1'
);
PREPARE uk_user_stmt FROM @stmt;
EXECUTE uk_user_stmt;
DEALLOCATE PREPARE uk_user_stmt;

-- 어드민 목록: status 필터 + submitted_at 내림차순 정렬용
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND INDEX_NAME = 'ix_seller_applications_status_submitted_at'
    ),
    'ALTER TABLE seller_applications ADD INDEX ix_seller_applications_status_submitted_at (status, submitted_at)',
    'SELECT 1'
);
PREPARE ix_status_stmt FROM @stmt;
EXECUTE ix_status_stmt;
DEALLOCATE PREPARE ix_status_stmt;

-- 어드민 상세: 동일 사업자번호 과거 신청 이력 대조용
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND INDEX_NAME = 'ix_seller_applications_business_reg_no'
    ),
    'ALTER TABLE seller_applications ADD INDEX ix_seller_applications_business_reg_no (business_reg_no)',
    'SELECT 1'
);
PREPARE ix_brn_stmt FROM @stmt;
EXECUTE ix_brn_stmt;
DEALLOCATE PREPARE ix_brn_stmt;

-- 서류는 신청서당 종류별 1장(교체 의미론). 동시 이중 업로드도 이 UNIQUE가 막는다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_documents'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'application_documents'
          AND INDEX_NAME = 'uk_application_documents_application_id_document_type'
    ),
    'ALTER TABLE application_documents ADD CONSTRAINT uk_application_documents_application_id_document_type UNIQUE (application_id, document_type)',
    'SELECT 1'
);
PREPARE uk_doc_stmt FROM @stmt;
EXECUTE uk_doc_stmt;
DEALLOCATE PREPARE uk_doc_stmt;

-- 셀러당 정산계좌 1개 — 엔티티 선언(uk_settlement_accounts_seller_id)의 실DB 반영 보장
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_accounts'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'settlement_accounts'
          AND INDEX_NAME = 'uk_settlement_accounts_seller_id'
    ),
    'ALTER TABLE settlement_accounts ADD CONSTRAINT uk_settlement_accounts_seller_id UNIQUE (seller_id)',
    'SELECT 1'
);
PREPARE uk_acct_stmt FROM @stmt;
EXECUTE uk_acct_stmt;
DEALLOCATE PREPARE uk_acct_stmt;
