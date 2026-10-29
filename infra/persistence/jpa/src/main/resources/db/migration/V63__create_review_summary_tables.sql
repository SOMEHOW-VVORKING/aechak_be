-- 리뷰 AI 요약(SCRUM-297). AI 서버가 리뷰마다 한 번 뽑은 장단점을 영구 보관하고, 그 집계로 만든 범위별 요약을 둔다.
-- 번호 주의: V62(리뷰 펫 컬럼, SCRUM-326) 다음 번호다. 머지 직전에 develop 최고 번호를 다시 확인한다.
-- CREATE TABLE IF NOT EXISTS로 만든다. dev는 Flyway와 ddl-auto(update)를 함께 쓰기 때문이다.

CREATE TABLE IF NOT EXISTS review_aspect_extractions
(
    review_id            BIGINT      NOT NULL,
    product_id           BIGINT      NOT NULL,
    status               VARCHAR(20) NOT NULL COMMENT 'EXTRACTED면 그 뒤로 바뀌지 않음',
    exclude_reason       VARCHAR(20) NULL COMMENT '다시 보내도 결과가 같은 사유로 빠졌을 때만',
    failed_attempt_count INT         NOT NULL DEFAULT 0,
    extract_version      VARCHAR(40) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (review_id),
    INDEX ix_review_aspect_extractions_product (product_id),
    CONSTRAINT fk_review_aspect_extractions_review FOREIGN KEY (review_id) REFERENCES reviews (id)
);

CREATE TABLE IF NOT EXISTS review_aspects
(
    review_id   BIGINT       NOT NULL,
    sort_order  INT          NOT NULL,
    aspect_code VARCHAR(30)  NOT NULL,
    polarity    VARCHAR(5)   NOT NULL,
    label       VARCHAR(100) NOT NULL,
    quote       VARCHAR(500) NOT NULL COMMENT '리뷰 노출 본문에서 그대로 떼어 온 문장',
    PRIMARY KEY (review_id, sort_order),
    CONSTRAINT fk_review_aspects_extraction FOREIGN KEY (review_id) REFERENCES review_aspect_extractions (review_id)
);

CREATE TABLE IF NOT EXISTS review_summaries
(
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    product_id            BIGINT      NOT NULL,
    scope_key             VARCHAR(30) NOT NULL COMMENT 'PRODUCT 또는 체급 묶음 키(DOG:DOG_UNDER_5 등)',
    basis_signature       VARCHAR(60) NOT NULL COMMENT '요약에 쓴 노출 리뷰 묶음의 지문',
    analyzed_review_count INT         NOT NULL,
    enough_reviews        BIT         NOT NULL,
    cons_absence_reason   VARCHAR(40) NULL,
    request_id            VARCHAR(40) NOT NULL,
    generated_at          DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_summaries_product_scope UNIQUE (product_id, scope_key)
);

CREATE TABLE IF NOT EXISTS review_summary_points
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    review_summary_id BIGINT       NOT NULL,
    polarity          VARCHAR(5)   NOT NULL,
    aspect_code       VARCHAR(30)  NOT NULL,
    headline          VARCHAR(200) NOT NULL,
    mention_count     INT          NOT NULL,
    sort_order        INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_summary_points_summary FOREIGN KEY (review_summary_id) REFERENCES review_summaries (id)
);

CREATE TABLE IF NOT EXISTS review_summary_quotes
(
    review_summary_point_id BIGINT       NOT NULL,
    sort_order              INT          NOT NULL,
    review_id               BIGINT       NOT NULL,
    quote                   VARCHAR(500) NOT NULL,
    PRIMARY KEY (review_summary_point_id, sort_order),
    CONSTRAINT fk_review_summary_quotes_point FOREIGN KEY (review_summary_point_id) REFERENCES review_summary_points (id)
);

CREATE TABLE IF NOT EXISTS review_summary_requests
(
    product_id          BIGINT       NOT NULL,
    request_id          VARCHAR(40)  NULL,
    requested_at        DATETIME(6)  NULL,
    retry_after         DATETIME(6)  NULL COMMENT '이 시각 전에는 다시 요청하지 않음',
    synced_signature    VARCHAR(60)  NULL COMMENT '모든 범위가 최신이라고 확인한 때의 상품 노출 리뷰 지문',
    expected_signatures VARCHAR(255) NULL COMMENT '마지막 요청의 범위별 기대 지문',
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (product_id)
);
