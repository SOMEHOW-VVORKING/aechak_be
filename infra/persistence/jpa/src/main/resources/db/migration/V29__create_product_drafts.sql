-- 상품 등록 폼의 임시저장본. 전 필드가 NULL 가능하다 — 폼을 채우는 중에도 저장이 되어야 해서
-- 필수값 검증은 정식 등록 시점(products)에만 걸린다.
-- payload는 중첩 입력(추가·상세 이미지 키, 옵션 그룹·조합)을 셀러가 보낸 모양 그대로 담는다.
-- 서버가 해석하지 않는 폼 상태라 컬럼으로 펼치지 않고 JSON 한 덩어리로 둔다.
-- seller_id는 값 참조라 FK 없음(레포 규약). created_at/updated_at은 BaseEntity가 채운다.

CREATE TABLE IF NOT EXISTS product_drafts
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    public_id           VARCHAR(26)   NOT NULL,              -- ULID. 외부에 내보내는 식별자
    seller_id           BIGINT        NOT NULL,
    category_id         BIGINT        NULL,
    product_name        VARCHAR(255)  NULL,
    description         TEXT          NULL,
    regular_price       BIGINT        NULL,
    discount_price      BIGINT        NULL,
    discount_start_at   DATETIME(6)   NULL,
    discount_end_at     DATETIME(6)   NULL,
    thumbnail_image_key VARCHAR(1024) NULL,                  -- 승격 전 tmp 키. 정식 등록 때 상품 등록 경로가 승격한다
    payload             JSON          NOT NULL,
    -- 셀러가 저장을 누른 시각. 감사 컬럼(updated_at)은 flush 때 다시 찍혀 응답과 어긋나고,
    -- 내용이 같은 재저장은 dirty로 잡히지 않아 아예 움직이지 않는다. 보관 기간 기산점도 이 컬럼이다
    saved_at            DATETIME(6)   NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_draft_public_id UNIQUE (public_id),
    -- 셀러별 목록·상한 계산과 보관 기간 정리가 모두 (seller_id, saved_at)으로 걸린다
    INDEX ix_product_draft_seller (seller_id, saved_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
