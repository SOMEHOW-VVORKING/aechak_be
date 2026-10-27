-- 관리자 검토 큐. AI가 문제를 찾았거나 판단하지 못한 상품이 들어옴.
-- 번호 주의: develop이 V35까지이고 V36~V38은 열린 SNS PR, V39는 앞선 검수 스키마 PR이 점유해
-- 그 다음 번호를 받음. 머지 순서를 맞추려고 V39에서 옮겨 왔고 본문은 그대로다.
-- 상품마다 열린 행(PENDING_REVIEW)은 하나뿐이고 DB 제약이 아니라 상품 행 잠금으로 지킴. 닫힌 행은 이력으로 남김.
-- detection_stage, reason_summary, locked_admin_id, locked_at은 ERD 컬럼이지만 아직 채우는 경로가 없음.
-- target_id, owner_id는 값 참조라 FK 없음. created_at/updated_at은 BaseEntity가 채움.
CREATE TABLE IF NOT EXISTS product_review_queue_items
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    target_type     VARCHAR(20)  NOT NULL,              -- PRODUCT / REVIEW
    target_id       BIGINT       NOT NULL,
    target_name     VARCHAR(255) NULL,                  -- 행을 열거나 갱신한 시점의 상품명
    owner_id        BIGINT       NOT NULL,              -- 셀러 users.id
    block_source    VARCHAR(30)  NOT NULL,              -- AI_DETECTION / REPORT_ACCUMULATION
    detection_stage VARCHAR(30)  NULL,
    reason_summary  VARCHAR(255) NULL,
    status          VARCHAR(30)  NOT NULL,              -- PENDING_REVIEW / APPROVED / REJECTED / SUPERSEDED
    blinded_at      DATETIME(6)  NOT NULL,              -- 행을 열었거나 다시 연 판정의 시각
    locked_admin_id BIGINT       NULL,
    locked_at       DATETIME(6)  NULL,
    reject_reason   VARCHAR(500) NULL,                  -- 관리자 반려 사유. 셀러에게 그대로 보임
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 판정 반영과 셀러 조회가 상품의 열린 행, 반려 이력을 찾는 경로
CREATE INDEX ix_product_review_queue_items_target
    ON product_review_queue_items (target_type, target_id, status);

-- 어드민 목록 경로
CREATE INDEX ix_product_review_queue_items_status
    ON product_review_queue_items (status, blinded_at);
