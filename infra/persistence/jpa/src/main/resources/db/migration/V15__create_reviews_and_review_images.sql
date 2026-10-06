-- reviews, review_images 스키마.
-- prod는 ddl-auto=none이라 스키마를 여기서 보장하고, 엔티티 자동 생성과 겹칠 수 있어 IF NOT EXISTS로 가드한다.

CREATE TABLE IF NOT EXISTS reviews
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    product_id            BIGINT       NOT NULL,
    option_combination_id BIGINT       NOT NULL,
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
