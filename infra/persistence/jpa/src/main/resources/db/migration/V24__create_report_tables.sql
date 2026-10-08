-- 신고 3종 테이블(product_reports, review_reports, user_reports)과 같은 대상에 대한 신고자별 중복 신고를 막는 UNIQUE.
-- UNIQUE 이름은 엔티티 @Table 선언과 같다.
-- 엔티티 자동 생성으로 테이블이 먼저 있던 DB(dev, local)는 CREATE를 건너뛰므로 아래 ALTER로 UNIQUE와 컬럼 타입을 맞춘다.
-- 기존 테이블에 같은 대상과 신고자의 행이 둘 이상 있으면 UNIQUE 추가가 실패한다. 지우고 다시 돌린다.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.

-- 상품 신고
CREATE TABLE IF NOT EXISTS product_reports
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    product_id  BIGINT       NOT NULL,                 -- 신고 대상 상품
    reporter_id BIGINT       NOT NULL,                 -- 신고자 users.id (값 참조)
    reason_code VARCHAR(30)  NOT NULL,                 -- FALSE_AD / INFO_ERROR / ILLEGAL / INAPPROPRIATE / OTHER
    reason_text VARCHAR(500) NULL,                     -- 상세 사유 (OTHER면 필수, 앱 검증)
    status      VARCHAR(30)  NOT NULL,                 -- RECEIVED / FORWARDED
    PRIMARY KEY (id),
    CONSTRAINT uk_product_reports_product_id_reporter_id UNIQUE (product_id, reporter_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_reports'
          AND INDEX_NAME = 'uk_product_reports_product_id_reporter_id'
    ),
    'ALTER TABLE product_reports ADD CONSTRAINT uk_product_reports_product_id_reporter_id UNIQUE (product_id, reporter_id)',
    'SELECT 1'
);
PREPARE uk_product_reports_stmt FROM @stmt;
EXECUTE uk_product_reports_stmt;
DEALLOCATE PREPARE uk_product_reports_stmt;

-- 엔티티 자동 생성이 만든 MySQL ENUM 컬럼을 VARCHAR(30)으로 맞춘다. 이미 VARCHAR(30)이면 바뀌는 것이 없다.
ALTER TABLE product_reports
    MODIFY COLUMN reason_code VARCHAR(30) NOT NULL,
    MODIFY COLUMN status      VARCHAR(30) NOT NULL;

-- 리뷰 신고
CREATE TABLE IF NOT EXISTS review_reports
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    review_id   BIGINT       NOT NULL,                 -- 신고 대상 리뷰
    reporter_id BIGINT       NOT NULL,                 -- 신고자 users.id (값 참조)
    reason_code VARCHAR(30)  NOT NULL,                 -- FRAUD / ABUSE / SPAM / INAPPROPRIATE / OTHER
    reason_text VARCHAR(500) NULL,                     -- 상세 사유 (OTHER면 필수, 앱 검증)
    status      VARCHAR(30)  NOT NULL,                 -- PENDING / RESOLVED / REJECTED
    PRIMARY KEY (id),
    CONSTRAINT uk_review_reports_review_id_reporter_id UNIQUE (review_id, reporter_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'review_reports'
          AND INDEX_NAME = 'uk_review_reports_review_id_reporter_id'
    ),
    'ALTER TABLE review_reports ADD CONSTRAINT uk_review_reports_review_id_reporter_id UNIQUE (review_id, reporter_id)',
    'SELECT 1'
);
PREPARE uk_review_reports_stmt FROM @stmt;
EXECUTE uk_review_reports_stmt;
DEALLOCATE PREPARE uk_review_reports_stmt;

ALTER TABLE review_reports
    MODIFY COLUMN reason_code VARCHAR(30) NOT NULL,
    MODIFY COLUMN status      VARCHAR(30) NOT NULL;

-- 사용자 신고
CREATE TABLE IF NOT EXISTS user_reports
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    reporter_id    BIGINT       NOT NULL,              -- 신고자 users.id
    target_user_id BIGINT       NOT NULL,              -- 신고 대상 users.id
    reason_code    VARCHAR(30)  NOT NULL,              -- FRAUD / ABUSE / SPAM / INAPPROPRIATE / OTHER
    reason_text    VARCHAR(500) NULL,                  -- 상세 사유 (OTHER면 필수, 앱 검증)
    status         VARCHAR(30)  NOT NULL,              -- RECEIVED / FORWARDED / RESOLVED / DISMISSED
    PRIMARY KEY (id),
    CONSTRAINT uk_user_reports_target_user_id_reporter_id UNIQUE (target_user_id, reporter_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_reports'
          AND INDEX_NAME = 'uk_user_reports_target_user_id_reporter_id'
    ),
    'ALTER TABLE user_reports ADD CONSTRAINT uk_user_reports_target_user_id_reporter_id UNIQUE (target_user_id, reporter_id)',
    'SELECT 1'
);
PREPARE uk_user_reports_stmt FROM @stmt;
EXECUTE uk_user_reports_stmt;
DEALLOCATE PREPARE uk_user_reports_stmt;

ALTER TABLE user_reports
    MODIFY COLUMN reason_code VARCHAR(30) NOT NULL,
    MODIFY COLUMN status      VARCHAR(30) NOT NULL;
