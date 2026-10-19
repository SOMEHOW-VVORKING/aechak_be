-- 결제 취소 원장. 원승인액(target_amount·real_paid_amount)은 취소에도 그대로 남고,
-- 줄어드는 것은 cancellable_amount 뿐이다. 누적 취소액은 아래 cancelled_amount가 따로 센다.
-- payments는 V24가 CREATE TABLE IF NOT EXISTS로 만든 테이블이라 ddl-auto가 먼저 같은 컬럼을
-- 만들어 둔 DB가 있을 수 있어, V10·V27과 같은 INFORMATION_SCHEMA 조건부 ALTER로 붙인다.

-- 누적 취소 확정액. 부분취소를 여러 번 받으면 더해진다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'cancelled_amount'
    ),
    'ALTER TABLE payments ADD COLUMN cancelled_amount BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE col0_stmt FROM @stmt;
EXECUTE col0_stmt;
DEALLOCATE PREPARE col0_stmt;

-- 기존 행을 0으로 채우려고 DEFAULT를 걸었을 뿐이라 바로 떼어 엔티티 선언과 같은 모양으로 둔다.
-- 기본값이 없는 컬럼에 다시 실행해도 무해하다.
ALTER TABLE payments
    ALTER COLUMN cancelled_amount DROP DEFAULT;

-- 마지막 취소 요청 시각. 요청만 하고 PG 응답을 못 받은 건(status=CANCEL_REQUESTED)을 찾는 기준
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'cancel_requested_at'
    ),
    'ALTER TABLE payments ADD COLUMN cancel_requested_at DATETIME(6) NULL',
    'SELECT 1'
);
PREPARE col1_stmt FROM @stmt;
EXECUTE col1_stmt;
DEALLOCATE PREPARE col1_stmt;

-- status에 CANCEL_REQUESTED / PARTIAL_CANCELLED / CANCELLED 세 값이 더해진다.
-- V24가 만든 테이블이면 VARCHAR(30)이라 그대로 들어가지만, ddl-auto가 먼저 만든 DB라면
-- Hibernate가 네이티브 enum('PENDING','APPROVED','FAILED')으로 박아 둬 새 값 쓰기가 실패한다.
-- V27이 다른 테이블에 한 것과 같은 조건부 수렴을 payments.status에도 적용한다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE payments MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en0_stmt FROM @stmt;
EXECUTE en0_stmt;
DEALLOCATE PREPARE en0_stmt;
