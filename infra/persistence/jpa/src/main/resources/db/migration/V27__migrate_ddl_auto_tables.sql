-- ddl-auto(update)로만 존재하던 테이블군을 Flyway 관리로 이관한다.
-- dev DB에는 Hibernate가 만든 동명 테이블이 이미 있으므로 모든 생성은 IF NOT EXISTS이고,
-- 인덱스·UNIQUE·추가 컬럼은 V10의 INFORMATION_SCHEMA 조건부 동적 SQL로 수렴시킨다.
-- 그래서 빈 DB와 기존 DB 어느 쪽에 적용해도 같은 모양으로 끝나고, 재실행도 안전하다.
--
-- 작성 규약
--  - FK 없음. BC 밖 참조(user_id, seller_id, product_id)도 같은 BC 부모-자식도 인덱스만 둔다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다(V21·V24와 같은 규약).
--  - 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 일치시킨다.


-- 셀러 상점. PK가 user_id다(별도 셀러 id 없음) — 유저 1명 = 셀러 1개.
CREATE TABLE IF NOT EXISTS sellers
(
    user_id                  BIGINT        NOT NULL,            -- 값 참조(user BC). 셀러 식별자이자 PK
    status                   VARCHAR(30)   NOT NULL,            -- ACTIVE / PAUSED / SUSPENDED / WITHDRAWAL_REQUESTED / WITHDRAWN
    previous_status          VARCHAR(30)   NULL,                -- 정지 해제 시 되돌릴 상태
    suspension_reason        VARCHAR(500)  NULL,
    status_changed_at        DATETIME(6)   NOT NULL,
    store_name               VARCHAR(100)  NOT NULL,
    brand_intro              VARCHAR(1000) NULL,
    profile_image_key        VARCHAR(1024) NULL,
    banner_image_key         VARCHAR(1024) NULL,
    zip_code                 VARCHAR(10)   NULL,
    road_address             VARCHAR(255)  NULL,
    detail_address           VARCHAR(255)  NULL,
    shipping_policy_text     TEXT          NULL,
    base_shipping_fee        BIGINT        NOT NULL,
    free_shipping_threshold  BIGINT        NULL,
    confirmed_order_count    INT           NOT NULL,            -- 원자 UPDATE로만 증가
    telesales_notice_status  VARCHAR(30)   NOT NULL,
    telesales_notice_sent_at DATETIME(6)   NULL,
    version                  BIGINT        NOT NULL,            -- 낙관적 락
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 입점 신청서. 유저당 1행을 재사용하는 모델이라 user_id에 UNIQUE가 붙는다(V10이 기존 DB에 이미 붙였다).
-- user_id를 NOT NULL로 두는 것은 V10의 결정을 빈 DB에서도 그대로 재현한 것이다 — 엔티티 선언은 nullable이지만 DB가 더 좁다.
CREATE TABLE IF NOT EXISTS seller_applications
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id           BIGINT       NULL,                      -- 승인 후 개점한 셀러(값 참조)
    user_id             BIGINT       NOT NULL,                  -- 값 참조(user BC)
    decided_admin_id    BIGINT       NULL,
    business_type       VARCHAR(30)  NOT NULL,
    telesales_number    VARCHAR(50)  NULL,
    status              VARCHAR(30)  NOT NULL,                  -- DRAFT / SUBMITTED / REVIEWING / APPROVED / REJECTED / CANCELLED
    rejection_reason    TEXT         NULL,
    submitted_at        DATETIME(6)  NULL,
    decided_at          DATETIME(6)  NULL,
    business_name       VARCHAR(100) NULL,
    business_reg_no     VARCHAR(20)  NULL,
    corp_reg_no         VARCHAR(20)  NULL,
    representative_name VARCHAR(50)  NULL,
    bank_code           VARCHAR(10)  NULL,
    account_number_enc  VARCHAR(256) NULL,                      -- AES 암호문 Base64. 평문 저장 금지
    account_holder      VARCHAR(50)  NULL,
    version             BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 신청 첨부 서류. 신청서당 종류별 1장(교체 의미론)이라 (application_id, document_type) UNIQUE.
CREATE TABLE IF NOT EXISTS application_documents
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    application_id BIGINT       NOT NULL,
    document_type  VARCHAR(40)  NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 심사 이력(append-only). 조회 경로는 application_id 하나뿐이라 그 인덱스만 둔다.
CREATE TABLE IF NOT EXISTS application_reviews
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    application_id    BIGINT      NOT NULL,
    reviewer_admin_id BIGINT      NOT NULL,                     -- 값 참조(user BC)
    decision          VARCHAR(30) NOT NULL,                     -- APPROVED / REJECTED / PENDING
    rejection_reason  TEXT        NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 셀러 정산 계좌. 셀러당 1행(UNIQUE는 V10이 기존 DB에 이미 붙였다).
CREATE TABLE IF NOT EXISTS settlement_accounts
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id           BIGINT       NOT NULL,                  -- 값 참조(seller BC)
    bank_code           VARCHAR(20)  NOT NULL,
    account_number_enc  VARCHAR(255) NOT NULL,                  -- AES 암호문 Base64
    account_holder_name VARCHAR(100) NOT NULL,
    portone_partner_id  VARCHAR(100) NULL,
    verification_status VARCHAR(30)  NOT NULL,                  -- UNVERIFIED / VERIFYING / VERIFIED / VERIFICATION_FAILED
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 주문 상태 전이 이력(append-only). 전이와 같은 트랜잭션에서 insert되므로 created_at이 전이 시각이다.
CREATE TABLE IF NOT EXISTS order_status_histories
(
    order_id     BIGINT      NOT NULL,
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    from_status  VARCHAR(30) NULL,                              -- 최초 생성 기록은 NULL
    to_status    VARCHAR(30) NOT NULL,
    triggered_by VARCHAR(30) NOT NULL,                          -- BUYER / SELLER / ADMIN / SYSTEM
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 택배사 마스터. PK가 택배사 코드고 shipments가 이 코드를 값으로 참조한다.
CREATE TABLE IF NOT EXISTS courier_masters
(
    courier_code            VARCHAR(30)  NOT NULL,
    courier_name            VARCHAR(60)  NOT NULL,
    tracking_format_pattern VARCHAR(255) NULL,                  -- 송장번호 형식 검증용. 미정이면 NULL
    is_active               BIT(1)       NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (courier_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 발송(송장) 1건. 최초 발송과 교환 재발송이 같은 테이블에 shipment_type으로 공존한다.
CREATE TABLE IF NOT EXISTS shipments
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    courier_code         VARCHAR(30) NOT NULL,
    order_id             BIGINT      NOT NULL,
    claim_id             BIGINT      NULL,                      -- 회수·재발송이면 해당 클레임
    tracking_number      VARCHAR(50) NOT NULL,
    dispatched_at        DATETIME(6) NULL,
    last_tracking_status VARCHAR(50) NULL,
    last_synced_at       DATETIME(6) NULL,
    delivered_at         DATETIME(6) NULL,
    shipment_type        VARCHAR(30) NOT NULL,                  -- INITIAL / EXCHANGE_RESHIP
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 클레임(취소·반품·교환). seller_id/order_id는 claim_items → order_items → orders 2홉 조인을 피하기 위한
-- 요청 시점 불변 스냅샷이다. seller_memo는 셀러 운영 메모이고 admin_reason(어드민 개입 사유)과 성격이 다르다.
CREATE TABLE IF NOT EXISTS claims
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    public_id              VARCHAR(26)  NOT NULL,               -- ULID. 외부 노출 식별자
    seller_id              BIGINT       NOT NULL,               -- 값 참조(seller BC). 셀러 목록·카운트 경로
    order_id               BIGINT       NOT NULL,
    claim_type             VARCHAR(30)  NOT NULL,               -- CANCEL / RETURN / EXCHANGE
    reason_code            VARCHAR(60)  NULL,
    return_cost_bearer     VARCHAR(30)  NULL,                   -- 반품비 부담 주체(귀책 아님)
    reason_detail          VARCHAR(500) NULL,
    status                 VARCHAR(30)  NOT NULL,
    rejection_reason       VARCHAR(500) NULL,
    admin_reason           VARCHAR(500) NULL,
    seller_memo            VARCHAR(500) NULL,
    refund_status          VARCHAR(30)  NULL,
    settlement_status      VARCHAR(30)  NULL,
    version                INT          NOT NULL,               -- 낙관적 락
    requested_at           DATETIME(6)  NULL,
    seller_approval_status VARCHAR(30)  NULL,
    auto_approve_due_at    DATETIME(6)  NULL,                   -- 자동승인 배치 스캔 기준
    clawback_deposited_at  DATETIME(6)  NULL,
    approved_at            DATETIME(6)  NULL,
    collection_started_at  DATETIME(6)  NULL,
    resolved_at            DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 클레임 대상 주문품목과 수량·환불액. 부분취소의 단위.
CREATE TABLE IF NOT EXISTS claim_items
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    claim_id      BIGINT      NOT NULL,
    order_item_id BIGINT      NOT NULL,
    quantity      INT         NOT NULL,                         -- 재고 복원 근거
    refund_amount BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ── 기존(ddl-auto 생성) 테이블에 없는 컬럼 보강 ────────────────────────────────
-- 위 CREATE TABLE이 만든 빈 DB에서는 이미 있으므로 전부 건너뛴다.

-- orders.base_shipping_fee_snapshot — 주문 시점 셀러 기본 배송비. 부분취소 배송비 재계산의 근거
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'base_shipping_fee_snapshot'
    ),
    'ALTER TABLE orders ADD COLUMN base_shipping_fee_snapshot BIGINT NULL',
    'SELECT 1'
);
PREPARE col0_stmt FROM @stmt;
EXECUTE col0_stmt;
DEALLOCATE PREPARE col0_stmt;

-- orders.free_shipping_threshold_snapshot — 주문 시점 무료배송 임계. 임계가 없던 주문은 NULL
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'free_shipping_threshold_snapshot'
    ),
    'ALTER TABLE orders ADD COLUMN free_shipping_threshold_snapshot BIGINT NULL',
    'SELECT 1'
);
PREPARE col1_stmt FROM @stmt;
EXECUTE col1_stmt;
DEALLOCATE PREPARE col1_stmt;

-- claims.seller_id — 비정규화 스냅샷(조인 2홉 회피)
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'seller_id'
    ),
    'ALTER TABLE claims ADD COLUMN seller_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE col2_stmt FROM @stmt;
EXECUTE col2_stmt;
DEALLOCATE PREPARE col2_stmt;

-- claims.order_id — 비정규화 스냅샷(조인 2홉 회피)
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'order_id'
    ),
    'ALTER TABLE claims ADD COLUMN order_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE col3_stmt FROM @stmt;
EXECUTE col3_stmt;
DEALLOCATE PREPARE col3_stmt;

-- claims.seller_memo — 셀러 운영 메모
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'seller_memo'
    ),
    'ALTER TABLE claims ADD COLUMN seller_memo VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE col4_stmt FROM @stmt;
EXECUTE col4_stmt;
DEALLOCATE PREPARE col4_stmt;

-- ── 인덱스·UNIQUE 수렴 ────────────────────────────────────────────────────────
-- 빈 DB든 ddl-auto가 만든 DB든 같은 경로를 타게 하려고 CREATE TABLE에 인라인으로 두지 않고
-- 전부 조건부 ALTER로 붙인다. 두 경로의 결과가 같다는 것이 이 PR의 핵심 불변식이다.

-- seller_applications.uk_seller_applications_user_id — 유저당 신청 1행 불변식의 DB 방어선
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications' AND INDEX_NAME = 'uk_seller_applications_user_id'
    ),
    'ALTER TABLE seller_applications ADD CONSTRAINT uk_seller_applications_user_id UNIQUE (user_id)',
    'SELECT 1'
);
PREPARE ix0_stmt FROM @stmt;
EXECUTE ix0_stmt;
DEALLOCATE PREPARE ix0_stmt;

-- seller_applications.ix_seller_applications_status_submitted_at — 어드민 목록: status 필터 + 제출일 정렬
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications' AND INDEX_NAME = 'ix_seller_applications_status_submitted_at'
    ),
    'ALTER TABLE seller_applications ADD INDEX ix_seller_applications_status_submitted_at (status, submitted_at)',
    'SELECT 1'
);
PREPARE ix1_stmt FROM @stmt;
EXECUTE ix1_stmt;
DEALLOCATE PREPARE ix1_stmt;

-- seller_applications.ix_seller_applications_business_reg_no — 어드민 상세: 동일 사업자번호 과거 이력 대조
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications' AND INDEX_NAME = 'ix_seller_applications_business_reg_no'
    ),
    'ALTER TABLE seller_applications ADD INDEX ix_seller_applications_business_reg_no (business_reg_no)',
    'SELECT 1'
);
PREPARE ix2_stmt FROM @stmt;
EXECUTE ix2_stmt;
DEALLOCATE PREPARE ix2_stmt;

-- application_documents.uk_application_documents_application_id_document_type — 신청서당 서류 종류별 1장. 신청서 기준 조회도 이 인덱스가 받는다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_documents'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_documents' AND INDEX_NAME = 'uk_application_documents_application_id_document_type'
    ),
    'ALTER TABLE application_documents ADD CONSTRAINT uk_application_documents_application_id_document_type UNIQUE (application_id, document_type)',
    'SELECT 1'
);
PREPARE ix3_stmt FROM @stmt;
EXECUTE ix3_stmt;
DEALLOCATE PREPARE ix3_stmt;

-- application_reviews.idx_application_reviews_application_id — 심사 이력의 유일한 조회 경로
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_reviews'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_reviews' AND INDEX_NAME = 'idx_application_reviews_application_id'
    ),
    'ALTER TABLE application_reviews ADD INDEX idx_application_reviews_application_id (application_id)',
    'SELECT 1'
);
PREPARE ix4_stmt FROM @stmt;
EXECUTE ix4_stmt;
DEALLOCATE PREPARE ix4_stmt;

-- settlement_accounts.uk_settlement_accounts_seller_id — 셀러당 계좌 1행
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_accounts'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_accounts' AND INDEX_NAME = 'uk_settlement_accounts_seller_id'
    ),
    'ALTER TABLE settlement_accounts ADD CONSTRAINT uk_settlement_accounts_seller_id UNIQUE (seller_id)',
    'SELECT 1'
);
PREPARE ix5_stmt FROM @stmt;
EXECUTE ix5_stmt;
DEALLOCATE PREPARE ix5_stmt;

-- order_status_histories.idx_order_status_histories_order_id — 주문 이력 시간순 조회(id가 곧 삽입 순서)
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_status_histories'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_status_histories' AND INDEX_NAME = 'idx_order_status_histories_order_id'
    ),
    'ALTER TABLE order_status_histories ADD INDEX idx_order_status_histories_order_id (order_id, id)',
    'SELECT 1'
);
PREPARE ix6_stmt FROM @stmt;
EXECUTE ix6_stmt;
DEALLOCATE PREPARE ix6_stmt;

-- shipments.idx_shipments_order_id — 주문 상세의 배송 추적
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND INDEX_NAME = 'idx_shipments_order_id'
    ),
    'ALTER TABLE shipments ADD INDEX idx_shipments_order_id (order_id)',
    'SELECT 1'
);
PREPARE ix7_stmt FROM @stmt;
EXECUTE ix7_stmt;
DEALLOCATE PREPARE ix7_stmt;

-- shipments.idx_shipments_claim_id — 클레임 회수·재발송 송장 조회
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND INDEX_NAME = 'idx_shipments_claim_id'
    ),
    'ALTER TABLE shipments ADD INDEX idx_shipments_claim_id (claim_id)',
    'SELECT 1'
);
PREPARE ix8_stmt FROM @stmt;
EXECUTE ix8_stmt;
DEALLOCATE PREPARE ix8_stmt;

-- shipments.idx_shipments_dispatched_at — 집화·배송완료 추적 동기화 배치 스캔
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND INDEX_NAME = 'idx_shipments_dispatched_at'
    ),
    'ALTER TABLE shipments ADD INDEX idx_shipments_dispatched_at (dispatched_at, delivered_at)',
    'SELECT 1'
);
PREPARE ix9_stmt FROM @stmt;
EXECUTE ix9_stmt;
DEALLOCATE PREPARE ix9_stmt;

-- claims.uk_claims_public_id — 외부 식별자 중복 방어선
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND INDEX_NAME = 'uk_claims_public_id'
    ),
    'ALTER TABLE claims ADD CONSTRAINT uk_claims_public_id UNIQUE (public_id)',
    'SELECT 1'
);
PREPARE ix10_stmt FROM @stmt;
EXECUTE ix10_stmt;
DEALLOCATE PREPARE ix10_stmt;

-- claims.idx_claims_seller_id_status — 셀러 클레임 목록·대시보드 카운트
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND INDEX_NAME = 'idx_claims_seller_id_status'
    ),
    'ALTER TABLE claims ADD INDEX idx_claims_seller_id_status (seller_id, status, id)',
    'SELECT 1'
);
PREPARE ix11_stmt FROM @stmt;
EXECUTE ix11_stmt;
DEALLOCATE PREPARE ix11_stmt;

-- claims.idx_claims_order_id — 주문 상세의 클레임 노출, 자동확정 유보 판정
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND INDEX_NAME = 'idx_claims_order_id'
    ),
    'ALTER TABLE claims ADD INDEX idx_claims_order_id (order_id)',
    'SELECT 1'
);
PREPARE ix12_stmt FROM @stmt;
EXECUTE ix12_stmt;
DEALLOCATE PREPARE ix12_stmt;

-- claims.idx_claims_auto_approve_due_at — 클레임 자동승인 배치 스캔
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND INDEX_NAME = 'idx_claims_auto_approve_due_at'
    ),
    'ALTER TABLE claims ADD INDEX idx_claims_auto_approve_due_at (status, auto_approve_due_at)',
    'SELECT 1'
);
PREPARE ix13_stmt FROM @stmt;
EXECUTE ix13_stmt;
DEALLOCATE PREPARE ix13_stmt;

-- claim_items.idx_claim_items_claim_id — 클레임 대상 품목 조회
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_items'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_items' AND INDEX_NAME = 'idx_claim_items_claim_id'
    ),
    'ALTER TABLE claim_items ADD INDEX idx_claim_items_claim_id (claim_id)',
    'SELECT 1'
);
PREPARE ix14_stmt FROM @stmt;
EXECUTE ix14_stmt;
DEALLOCATE PREPARE ix14_stmt;

-- claim_items.idx_claim_items_order_item_id — 같은 주문품목에 진행 중 클레임이 있는지 판정
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_items'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_items' AND INDEX_NAME = 'idx_claim_items_order_item_id'
    ),
    'ALTER TABLE claim_items ADD INDEX idx_claim_items_order_item_id (order_item_id)',
    'SELECT 1'
);
PREPARE ix15_stmt FROM @stmt;
EXECUTE ix15_stmt;
DEALLOCATE PREPARE ix15_stmt;

-- orders.idx_orders_order_group_id — V21이 선언했지만 ddl-auto 환경에는 FK 보조 인덱스로만 있어 이름이 다르다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_order_group_id'
    ),
    'ALTER TABLE orders ADD INDEX idx_orders_order_group_id (order_group_id)',
    'SELECT 1'
);
PREPARE ix16_stmt FROM @stmt;
EXECUTE ix16_stmt;
DEALLOCATE PREPARE ix16_stmt;

-- orders.idx_orders_seller_id_status — 셀러 주문 목록
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_seller_id_status'
    ),
    'ALTER TABLE orders ADD INDEX idx_orders_seller_id_status (seller_id, status, id)',
    'SELECT 1'
);
PREPARE ix17_stmt FROM @stmt;
EXECUTE ix17_stmt;
DEALLOCATE PREPARE ix17_stmt;

-- orders.idx_orders_status_auto_confirm_due_at — 구매확정 자동화 배치 스캔
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_status_auto_confirm_due_at'
    ),
    'ALTER TABLE orders ADD INDEX idx_orders_status_auto_confirm_due_at (status, auto_confirm_due_at)',
    'SELECT 1'
);
PREPARE ix18_stmt FROM @stmt;
EXECUTE ix18_stmt;
DEALLOCATE PREPARE ix18_stmt;

-- orders.idx_orders_seller_id_purchase_confirmed_at — 정산 대상 주문 스캔
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_seller_id_purchase_confirmed_at'
    ),
    'ALTER TABLE orders ADD INDEX idx_orders_seller_id_purchase_confirmed_at (seller_id, purchase_confirmed_at)',
    'SELECT 1'
);
PREPARE ix19_stmt FROM @stmt;
EXECUTE ix19_stmt;
DEALLOCATE PREPARE ix19_stmt;

-- ── 택배사 시드 ───────────────────────────────────────────────────────────────
-- 재실행해도 행이 늘지 않게 코드(PK) 충돌 시 이름만 갱신한다. 운영 중 비활성 처리한 택배사를
-- 되살리지 않도록 is_active는 건드리지 않는다.
INSERT INTO courier_masters (courier_code, courier_name, tracking_format_pattern, is_active, created_at, updated_at)
VALUES ('CJ', 'CJ대한통운', NULL, b'1', NOW(6), NOW(6)),
       ('LOTTE', '롯데택배', NULL, b'1', NOW(6), NOW(6)),
       ('HANJIN', '한진택배', NULL, b'1', NOW(6), NOW(6)),
       ('LOGEN', '로젠택배', NULL, b'1', NOW(6), NOW(6)),
       ('POST', '우체국택배', NULL, b'1', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE courier_name = VALUES(courier_name),
                        updated_at   = NOW(6);
