-- 운영자 리뷰 블라인드. reviews에 비노출 처리의 주체·사유를 붙이고, 엔티티만 있고 테이블이 없던
-- review_reports를 만든다.
--
-- 조건부 ALTER인 이유: dev는 Flyway와 ddl-auto(update)가 함께 돌아 엔티티 선언을 본 Hibernate가
-- 같은 컬럼을 먼저 만들어 둔 DB가 있을 수 있다. 그 상태에서 맨 ALTER를 내보내면 중복 컬럼으로
-- 실패하고, 한 번 실패한 파일은 그 뒤로 영구 미적용으로 남는다 (V10·V27·V28과 같은 수렴 방식).

-- 비노출 처리 주체. 작성 시점 금칙어 판정은 AI, 운영자 수동 처리는 ADMIN
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'blind_actor'
    ),
    'ALTER TABLE reviews ADD COLUMN blind_actor VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE col0_stmt FROM @stmt;
EXECUTE col0_stmt;
DEALLOCATE PREPARE col0_stmt;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'blind_reason_code'
    ),
    'ALTER TABLE reviews ADD COLUMN blind_reason_code VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE col1_stmt FROM @stmt;
EXECUTE col1_stmt;
DEALLOCATE PREPARE col1_stmt;

-- 운영자가 쓴 사유 메모. 자동 처리는 비어 있다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'blind_reason_text'
    ),
    'ALTER TABLE reviews ADD COLUMN blind_reason_text VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE col2_stmt FROM @stmt;
EXECUTE col2_stmt;
DEALLOCATE PREPARE col2_stmt;

-- 블라인드가 걸린 시각. 해제하면 다시 NULL이 된다 — "지금 가려져 있는가"의 축은 review_status다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'blinded_at'
    ),
    'ALTER TABLE reviews ADD COLUMN blinded_at DATETIME(6) NULL',
    'SELECT 1'
);
PREPARE col3_stmt FROM @stmt;
EXECUTE col3_stmt;
DEALLOCATE PREPARE col3_stmt;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'blinded_by_admin_id'
    ),
    'ALTER TABLE reviews ADD COLUMN blinded_by_admin_id BIGINT NULL',
    'SELECT 1'
);
PREPARE col4_stmt FROM @stmt;
EXECUTE col4_stmt;
DEALLOCATE PREPARE col4_stmt;

-- 운영자 삭제와 작성자 삭제를 가르는 축. 작성자가 지운 행은 NULL로 남는다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'deleted_by_admin_id'
    ),
    'ALTER TABLE reviews ADD COLUMN deleted_by_admin_id BIGINT NULL',
    'SELECT 1'
);
PREPARE col5_stmt FROM @stmt;
EXECUTE col5_stmt;
DEALLOCATE PREPARE col5_stmt;

-- 이 파일 전에 BLOCKED가 된 행은 전부 작성 시점 금칙어 판정의 결과다 — 그때는 운영자 블라인드
-- 경로가 없었다. 백필하지 않으면 과거 행의 처리 주체가 화면에서 전부 비어 "누가 가렸는지 모르는
-- 블라인드"로 보인다. 닫힌 뒤 더 쓰지 않는 상태라 updated_at이 그 행이 가려진 시각이다.
UPDATE reviews
SET blind_actor = 'AI',
    blind_reason_code = 'PROFANITY',
    blinded_at = updated_at
WHERE review_status = 'BLOCKED'
  AND blind_actor IS NULL;

-- 상품 필터 없이 상태로만 좁히는 운영자 목록의 경로. 기존 인덱스는 전부 product_id가 선두라
-- 상품을 고르지 않은 목록이 쓸 수 없다.
-- 조건부로 만드는 이유는 V51과 같다 — 이 파일이 한 번 실패해 미적용으로 남으면 ddl-auto가
-- 엔티티 선언을 보고 같은 이름 인덱스를 먼저 만들고, MySQL에 CREATE INDEX IF NOT EXISTS가 없다.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'reviews'
                     AND INDEX_NAME = 'ix_reviews_status_id');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_reviews_status_id ON reviews (review_status, id)',
              'DO 0');
PREPARE idx_stmt FROM @sql;
EXECUTE idx_stmt;
DEALLOCATE PREPARE idx_stmt;

-- 리뷰 신고. 엔티티(ReviewReport)는 있었지만 마이그레이션이 없어 Flyway 스키마에 테이블이
-- 없었다 — ddl-auto가 도는 환경에만 존재했다. 운영자 상세가 신고 이력을 읽으므로 여기서 만든다.
-- 접수 경로(신고 API)는 아직 없어 당분간 빈 테이블이다.
-- review_reports -> reviews는 같은 BC 안이라 FK를 건다(BC 경계 밖 참조인 reporter_id는 걸지 않는다).
CREATE TABLE IF NOT EXISTS review_reports
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    review_id   BIGINT      NOT NULL,
    reporter_id BIGINT      NOT NULL,
    reason_code VARCHAR(30) NOT NULL,
    reason_text VARCHAR(500) NULL,
    status      VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    INDEX ix_review_reports_review_id (review_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
