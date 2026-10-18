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

-- NOT NULL 좁히기가 남은 NULL을 조용히 0으로 바꾸지 않게 이 마이그레이션 세션만 strict로 고정한다.
SET SESSION sql_mode = IF(@@SESSION.sql_mode = '', 'STRICT_ALL_TABLES',
                          CONCAT(@@SESSION.sql_mode, ',STRICT_ALL_TABLES'));


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

-- claims.seller_id — 비정규화 스냅샷. 아래 백필 후 NOT NULL로 좁힌다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'seller_id'
    ),
    'ALTER TABLE claims ADD COLUMN seller_id BIGINT NULL',
    'SELECT 1'
);
PREPARE col2_stmt FROM @stmt;
EXECUTE col2_stmt;
DEALLOCATE PREPARE col2_stmt;

-- claims.order_id — 비정규화 스냅샷. 아래 백필 후 NOT NULL로 좁힌다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'order_id'
    ),
    'ALTER TABLE claims ADD COLUMN order_id BIGINT NULL',
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

-- ── claims 비정규화 컬럼 백필 ─────────────────────────────────────────────────
-- 위에서 NULL로 추가했으므로 기존 행을 주문 경로로 채운 뒤 NOT NULL로 좁힌다.
-- NOT NULL로 바로 추가하면 MySQL이 기존 행을 암묵적 기본값 0으로 채워 조용히 오염된다.
-- 품목 전부가 같은 한 주문으로 이어질 때만 채운다. FK가 없어 주문·주문품목이 사라진 품목이 섞여
-- 있을 수 있는데, 그런 품목을 INNER JOIN으로 떨어뜨리면 남은 품목만 보고 "주문 하나"로 오판한다.
-- 채우지 못한 행이 있으면 다음 NOT NULL 좁히기가 실패해 사람이 보게 된다(조용히 0을 넣는 것보다 낫다).
UPDATE claims c
    JOIN (SELECT ci.claim_id,
                 MIN(o.id)              AS order_id,
                 MIN(o.seller_id)       AS seller_id,
                 COUNT(*)               AS item_count,
                 COUNT(o.id)            AS linked_item_count,
                 COUNT(DISTINCT o.id)   AS order_count
          FROM claim_items ci
                   LEFT JOIN order_items oi ON oi.id = ci.order_item_id
                   LEFT JOIN orders o ON o.id = oi.order_id
          GROUP BY ci.claim_id) src
    ON src.claim_id = c.id
   AND src.order_count = 1
   AND src.linked_item_count = src.item_count
SET c.order_id   = src.order_id,
    c.seller_id  = src.seller_id,
    c.updated_at = NOW(6)
WHERE c.order_id IS NULL
   OR c.seller_id IS NULL;

-- claims.order_id 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'order_id' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE claims MODIFY COLUMN order_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE nn0_stmt FROM @stmt;
EXECUTE nn0_stmt;
DEALLOCATE PREPARE nn0_stmt;

-- claims.seller_id 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'seller_id' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE claims MODIFY COLUMN seller_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE nn1_stmt FROM @stmt;
EXECUTE nn1_stmt;
DEALLOCATE PREPARE nn1_stmt;

-- ── seller_applications.user_id NOT NULL 보강 ────────────────────────────────
-- V10이 이 제약을 걸지만, V10 적용 시점에 테이블이 없던 DB(그 뒤 ddl-auto가 만든 DB)에서는
-- 건너뛰어져 nullable로 남는다. 아래 UNIQUE는 NULL 중복을 막지 못하므로 여기서 먼저 좁힌다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'user_id' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE seller_applications MODIFY COLUMN user_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE sa_user_id_stmt FROM @stmt;
EXECUTE sa_user_id_stmt;
DEALLOCATE PREPARE sa_user_id_stmt;

-- ── enum 컬럼 VARCHAR 수렴 ───────────────────────────────────────────────────
-- Hibernate는 @Enumerated(STRING)을 MySQL 네이티브 enum으로 만들지만 이 레포의 마이그레이션은
-- VARCHAR을 쓴다(V21·V24 규약). 네이티브 enum으로 남겨 두면 그 DB는 enum 값 목록이 테이블에
-- 박혀 있어, 코드에 상태값이 추가되면 쓰기가 실패한다. 그래서 기존 enum 컬럼을 VARCHAR로 바꾼다.
-- 값은 라벨 문자열이 그대로 보존된다.

-- sellers.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE sellers MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en0_stmt FROM @stmt;
EXECUTE en0_stmt;
DEALLOCATE PREPARE en0_stmt;

-- sellers.previous_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers'
          AND COLUMN_NAME = 'previous_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE sellers MODIFY COLUMN previous_status VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en1_stmt FROM @stmt;
EXECUTE en1_stmt;
DEALLOCATE PREPARE en1_stmt;

-- sellers.telesales_notice_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers'
          AND COLUMN_NAME = 'telesales_notice_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE sellers MODIFY COLUMN telesales_notice_status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en2_stmt FROM @stmt;
EXECUTE en2_stmt;
DEALLOCATE PREPARE en2_stmt;

-- seller_applications.business_type
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'business_type' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE seller_applications MODIFY COLUMN business_type VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en3_stmt FROM @stmt;
EXECUTE en3_stmt;
DEALLOCATE PREPARE en3_stmt;

-- seller_applications.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_applications'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE seller_applications MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en4_stmt FROM @stmt;
EXECUTE en4_stmt;
DEALLOCATE PREPARE en4_stmt;

-- application_documents.document_type
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_documents'
          AND COLUMN_NAME = 'document_type' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE application_documents MODIFY COLUMN document_type VARCHAR(40) NOT NULL',
    'SELECT 1'
);
PREPARE en5_stmt FROM @stmt;
EXECUTE en5_stmt;
DEALLOCATE PREPARE en5_stmt;

-- application_reviews.decision
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_reviews'
          AND COLUMN_NAME = 'decision' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE application_reviews MODIFY COLUMN decision VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en6_stmt FROM @stmt;
EXECUTE en6_stmt;
DEALLOCATE PREPARE en6_stmt;

-- settlement_accounts.verification_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_accounts'
          AND COLUMN_NAME = 'verification_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE settlement_accounts MODIFY COLUMN verification_status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en7_stmt FROM @stmt;
EXECUTE en7_stmt;
DEALLOCATE PREPARE en7_stmt;

-- order_status_histories.from_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_status_histories'
          AND COLUMN_NAME = 'from_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE order_status_histories MODIFY COLUMN from_status VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en8_stmt FROM @stmt;
EXECUTE en8_stmt;
DEALLOCATE PREPARE en8_stmt;

-- order_status_histories.to_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_status_histories'
          AND COLUMN_NAME = 'to_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE order_status_histories MODIFY COLUMN to_status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en9_stmt FROM @stmt;
EXECUTE en9_stmt;
DEALLOCATE PREPARE en9_stmt;

-- order_status_histories.triggered_by
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_status_histories'
          AND COLUMN_NAME = 'triggered_by' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE order_status_histories MODIFY COLUMN triggered_by VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en10_stmt FROM @stmt;
EXECUTE en10_stmt;
DEALLOCATE PREPARE en10_stmt;

-- shipments.shipment_type
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
          AND COLUMN_NAME = 'shipment_type' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE shipments MODIFY COLUMN shipment_type VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en11_stmt FROM @stmt;
EXECUTE en11_stmt;
DEALLOCATE PREPARE en11_stmt;

-- claims.claim_type
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'claim_type' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN claim_type VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en12_stmt FROM @stmt;
EXECUTE en12_stmt;
DEALLOCATE PREPARE en12_stmt;

-- claims.return_cost_bearer
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'return_cost_bearer' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN return_cost_bearer VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en13_stmt FROM @stmt;
EXECUTE en13_stmt;
DEALLOCATE PREPARE en13_stmt;

-- claims.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en14_stmt FROM @stmt;
EXECUTE en14_stmt;
DEALLOCATE PREPARE en14_stmt;

-- claims.refund_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'refund_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN refund_status VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en15_stmt FROM @stmt;
EXECUTE en15_stmt;
DEALLOCATE PREPARE en15_stmt;

-- claims.settlement_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'settlement_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN settlement_status VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en16_stmt FROM @stmt;
EXECUTE en16_stmt;
DEALLOCATE PREPARE en16_stmt;

-- claims.seller_approval_status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims'
          AND COLUMN_NAME = 'seller_approval_status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE claims MODIFY COLUMN seller_approval_status VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE en17_stmt FROM @stmt;
EXECUTE en17_stmt;
DEALLOCATE PREPARE en17_stmt;

-- orders.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE orders MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en18_stmt FROM @stmt;
EXECUTE en18_stmt;
DEALLOCATE PREPARE en18_stmt;

-- orders.confirm_type
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
          AND COLUMN_NAME = 'confirm_type' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE orders MODIFY COLUMN confirm_type VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE en19_stmt FROM @stmt;
EXECUTE en19_stmt;
DEALLOCATE PREPARE en19_stmt;

-- ── 인덱스·UNIQUE 수렴 ────────────────────────────────────────────────────────
-- 빈 DB든 ddl-auto가 만든 DB든 같은 경로를 타게 하려고 CREATE TABLE에 인라인으로 두지 않고
-- 전부 조건부 ALTER로 붙인다. 두 경로의 결과가 같다는 것이 이 PR의 핵심 불변식이다.
-- 판정은 이름 존재 여부다 — 같은 이름으로 정의가 다른 인덱스가 있으면 건너뛴다. 여기 이름은
-- 전부 이 마이그레이션이 처음 쓰거나 V10이 같은 정의로 만든 것이라 그 경우가 없다.

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
-- 코드(PK)가 이미 있으면 아무것도 바꾸지 않는다 — 운영에서 비활성 처리하거나 이름을 고친 행을
-- 재실행이 되돌리지 않게 한다. 기준 5종은 셀러센터가 쓰는 택배사 목록과 같다.
INSERT INTO courier_masters (courier_code, courier_name, tracking_format_pattern, is_active, created_at, updated_at)
VALUES ('CJ', 'CJ대한통운', NULL, b'1', NOW(6), NOW(6)),
       ('LOTTE', '롯데택배', NULL, b'1', NOW(6), NOW(6)),
       ('HANJIN', '한진택배', NULL, b'1', NOW(6), NOW(6)),
       ('LOGEN', '로젠택배', NULL, b'1', NOW(6), NOW(6)),
       ('POST', '우체국택배', NULL, b'1', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE courier_code = courier_code;
