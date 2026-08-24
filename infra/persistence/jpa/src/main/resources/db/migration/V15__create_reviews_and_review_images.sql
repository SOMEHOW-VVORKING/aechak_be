-- reviews, review_images 스키마.
CREATE TABLE IF NOT EXISTS reviews
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    product_id            BIGINT       NOT NULL,
    option_name_snapshot  VARCHAR(255) NOT NULL,
    order_item_id         BIGINT       NOT NULL,
    author_user_id        BIGINT       NOT NULL,
    rating                INT          NOT NULL,
    content               TEXT         NOT NULL,
    display_content       TEXT         NULL,
    review_status         VARCHAR(30)  NOT NULL,
    deleted_at            DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_order_item_id UNIQUE (order_item_id),
    INDEX ix_reviews_product_status_id (product_id, review_status, id),
    INDEX ix_reviews_product_status_rating_id (product_id, review_status, rating, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 주문 당시 옵션명을 order_items에 보존한다.
-- 기존 테이블은 아직 Flyway 관리 밖(ddl-auto 생성)이므로 새 DB에 테이블이 없으면 건너뛴다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'order_items'
          AND COLUMN_NAME = 'option_name_snapshot'
    ),
    'ALTER TABLE order_items ADD COLUMN option_name_snapshot VARCHAR(255) NULL AFTER option_combination_id',
    'SELECT 1'
);
PREPARE add_snapshot_stmt FROM @stmt;
EXECUTE add_snapshot_stmt;
DEALLOCATE PREPARE add_snapshot_stmt;

-- 기존 주문 품목은 연결된 옵션 조합의 현재 이름으로 한 번만 백필한다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'order_items'
          AND COLUMN_NAME = 'option_name_snapshot'
    ) AND EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'option_combinations'
    ),
    'UPDATE order_items oi JOIN option_combinations oc ON oc.id = oi.option_combination_id SET oi.option_name_snapshot = oc.name WHERE oi.option_name_snapshot IS NULL',
    'SELECT 1'
);
PREPARE backfill_snapshot_stmt FROM @stmt;
EXECUTE backfill_snapshot_stmt;
DEALLOCATE PREPARE backfill_snapshot_stmt;

-- 백필되지 않은 고아 주문 품목이 있으면 여기서 실패시켜 잘못된 빈 스냅샷을 만들지 않는다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'order_items'
          AND COLUMN_NAME = 'option_name_snapshot'
          AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE order_items MODIFY COLUMN option_name_snapshot VARCHAR(255) NOT NULL',
    'SELECT 1'
);
PREPARE not_null_snapshot_stmt FROM @stmt;
EXECUTE not_null_snapshot_stmt;
DEALLOCATE PREPARE not_null_snapshot_stmt;

CREATE TABLE IF NOT EXISTS review_images
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    review_id   BIGINT       NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    sort_order  INT          NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    INDEX ix_review_images_review_id (review_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
