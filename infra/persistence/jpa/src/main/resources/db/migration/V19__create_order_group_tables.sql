-- 주문그룹 생성(POST /order-groups) 저장소. 결제 단위(order_groups) / 셀러 단위(orders) / 옵션 조합 단위(order_items).
-- BC 밖 참조(buyer_id, seller_id, product_id 등)는 값 참조라 FK 없음. 같은 BC 부모-자식도 팀 컨벤션대로 FK 없이 인덱스만.
-- 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 맞춘다. created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.
CREATE TABLE IF NOT EXISTS order_groups
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    public_id             VARCHAR(26)  NOT NULL,              -- ULID. 주문번호이자 포트원 paymentId로 재사용
    buyer_id              BIGINT       NOT NULL,              -- 값 참조(user BC)
    delivery_address_id   BIGINT       NULL,                  -- 원본 배송지(값 참조). 스냅샷이 진실이고 이건 추적용
    receiver_name_enc     VARCHAR(255) NOT NULL,              -- 배송지 스냅샷. AES 암호문 Base64. 평문 상한 50자(150B)+프레임 29B → 240자
    contact_number_enc    VARCHAR(255) NOT NULL,              -- 배송지 스냅샷. AES 암호문 Base64. 평문 숫자 11자리면 56자라 여유
    zip_code              VARCHAR(255) NOT NULL,
    base_address          VARCHAR(512) NOT NULL,
    detail_address        VARCHAR(512) NULL,
    delivery_memo         VARCHAR(255) NULL,
    applied_coupon_id     BIGINT       NULL,                  -- 쿠폰은 MVP 제외, 컬럼만 선반영
    coupon_discount_amount BIGINT      NOT NULL,
    used_point            BIGINT       NOT NULL,              -- 적립금 복원 근거. PR 3까지는 0만 허용
    total_product_amount  BIGINT       NOT NULL,
    total_shipping_fee    BIGINT       NOT NULL,
    final_payment_amount  BIGINT       NOT NULL,              -- 상품 + 배송비 - 적립금. 서버 계산값
    status                VARCHAR(30)  NOT NULL,              -- PENDING_PAYMENT / PAID / PARTIALLY_CANCELLED / CANCELLED
    expires_at            DATETIME(6)  NULL,                  -- 생성 + 10분. 만료 배치 스캔 기준
    idempotency_key       VARCHAR(100) NOT NULL,              -- FE 생성 UUID. 중복 생성의 최후 방어선
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_group_public_id UNIQUE (public_id),
    CONSTRAINT uk_order_group_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_order_groups_status_expires_at (status, expires_at) -- 만료 배치가 결제대기 + 만료경과를 훑는 경로
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS orders
(
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    public_id                    VARCHAR(26)  NOT NULL,       -- ULID
    order_group_id               BIGINT       NOT NULL,
    status                       VARCHAR(30)  NOT NULL,       -- OrderStatus 8종, 생성 시 PENDING_PAYMENT
    seller_id                    BIGINT       NOT NULL,       -- 값 참조(seller BC)
    seller_name_snapshot         VARCHAR(255) NULL,           -- 주문 시점 상호. 셀러 개명·퇴점에도 이력 보존
    allocated_coupon_discount    BIGINT       NOT NULL,
    seller_shipping_fee          BIGINT       NOT NULL,
    purchase_confirmed_at        DATETIME(6)  NULL,
    purchase_confirm_notified_at DATETIME(6)  NULL,
    auto_confirm_due_at          DATETIME(6)  NULL,
    confirm_type                 VARCHAR(20)  NULL,           -- MANUAL / AUTO
    extension_count              INT          NOT NULL,
    created_at                   DATETIME(6)  NOT NULL,
    updated_at                   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_public_id UNIQUE (public_id),
    INDEX idx_orders_order_group_id (order_group_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_items
(
    id                        BIGINT      NOT NULL AUTO_INCREMENT,
    order_id                  BIGINT      NOT NULL,
    product_id                BIGINT      NOT NULL,           -- 값 참조(product BC)
    option_combination_id     BIGINT      NOT NULL,           -- 값 참조. 재고 복원 대상
    quantity                  INT         NOT NULL,           -- 재고 복원 근거
    unit_price_snapshot       BIGINT      NOT NULL,           -- 주문 시점 단가. 이후 가격 변경과 무관
    discount_allocated_amount BIGINT      NOT NULL,
    item_status               VARCHAR(30) NOT NULL,           -- 생성 시 ORDERED
    product_version_id        BIGINT      NOT NULL,           -- 주문 시점 상품 스냅샷 버전(값 참조)
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_items_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
