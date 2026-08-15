-- 계좌번호 저장 암호화(SLR-01 8/7 재결정) — 평문 컬럼을 AES 암호문(Base64) 컬럼으로 전환.
-- 대상 테이블은 아직 ddl-auto 관리(새 DB는 엔티티 선언대로 생성)라, 기존 DB에 컬럼이 있을 때만 전환한다(V10 선례).

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'account_number'
    ),
    'ALTER TABLE seller_applications CHANGE COLUMN account_number account_number_enc VARCHAR(256) NULL',
    'SELECT 1'
);
PREPARE rename_stmt FROM @stmt;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;

-- 평문 잔재 소거 — 미배포 기능이라 기존 값은 전부 암호문 형식이 아닌 무효 데이터다(복호화 불가로 어차피 못 읽는다).
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'account_number_enc'
    ),
    'UPDATE seller_applications SET account_number_enc = NULL',
    'SELECT 1'
);
PREPARE purge_stmt FROM @stmt;
EXECUTE purge_stmt;
DEALLOCATE PREPARE purge_stmt;
