-- 정산 원장(settlements)·지급(payouts)·집행 배치(settlement_execution_batches)를 Flyway 관리로 들이고,
-- 수수료율 정책 테이블(settlement_fee_policies)을 신설한다.
-- 앞의 세 테이블은 dev/local에서 ddl-auto(update)가 만들어 둔 상태라 생성은 전부 IF NOT EXISTS이고,
-- 보강 컬럼·인덱스·UNIQUE는 V10·V27의 INFORMATION_SCHEMA 조건부 동적 SQL로 수렴시킨다.
-- 그래서 빈 DB와 기존 DB 어느 쪽에 적용해도 같은 모양으로 끝나고, 재실행도 안전하다.
--
-- 작성 규약
--  - FK 없음. ddl-auto가 만들어 둔 settlements.payout_id·payouts.batch_id FK는 조건부로 떼고 인덱스만 남긴다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다(V21·V24·V27과 같은 규약).
--  - DEFAULT 절은 쓰지 않는다 — 엔티티 선언이 만들 DDL과 모양이 갈린다. 기본값은 애플리케이션이 채운다.
--  - 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 일치시킨다.

-- NOT NULL 좁히기가 남은 NULL을 조용히 0으로 바꾸지 않게 이 마이그레이션 동안만 strict로 고정하고
-- 끝에서 원래 값으로 되돌린다. Flyway는 앱과 같은 커넥션 풀을 쓰므로 세션 설정을 남기면 안 된다.
SET @saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = IF(@@SESSION.sql_mode = '', 'STRICT_ALL_TABLES',
                          CONCAT(@@SESSION.sql_mode, ',STRICT_ALL_TABLES'));


-- 정산 원장. 구매확정된 주문품목 1건당 1행이고, 멱등키가 중복 생성을 막는다.
-- order_id는 주문 단위 집계(셀러 정산 화면)를 위한 비정규화 값이다.
CREATE TABLE IF NOT EXISTS settlements
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    payout_id            BIGINT        NULL,                     -- 지급 묶음(같은 BC, FK 없음)
    seller_id            BIGINT        NOT NULL,                 -- 값 참조(seller BC)
    order_id             BIGINT        NOT NULL,                 -- 비정규화. 값 참조(order BC)
    order_item_id        BIGINT        NOT NULL,                 -- 값 참조(order BC)
    gross_amount         BIGINT        NOT NULL,                 -- 상품 금액
    shipping_fee         BIGINT        NOT NULL,                 -- 주문 셀러 배송비의 품목 안분액
    commission           BIGINT        NOT NULL,                 -- 플랫폼 수수료 금액
    pg_fee               BIGINT        NOT NULL,                 -- 결제 수수료 금액
    seller_coupon_burden BIGINT        NOT NULL,
    refund_deduction     BIGINT        NOT NULL,                 -- 클레임 환불 확정분 차감액
    net_amount           BIGINT        NOT NULL,                 -- 지급 예정액
    platform_fee_rate    DECIMAL(5, 4) NOT NULL,                 -- 생성 시점 요율 스냅샷
    pg_fee_rate          DECIMAL(5, 4) NOT NULL,
    settlement_month     VARCHAR(7)    NOT NULL,                 -- 구매확정일(KST) 연월 yyyy-MM
    expected_payout_date DATE          NOT NULL,                 -- 구매확정일 + 역일 5일
    status               VARCHAR(30)   NOT NULL,                 -- PENDING / ON_HOLD / PAID / CANCELLED
    idempotency_key      VARCHAR(100)  NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 셀러별 지급 1건. 송금키가 같은 배치·셀러에 지급을 두 번 만드는 것을 막는다.
CREATE TABLE IF NOT EXISTS payouts
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id                 BIGINT       NOT NULL,              -- 집행 배치(같은 BC, FK 없음)
    seller_id                BIGINT       NOT NULL,              -- 값 참조(seller BC)
    settlement_target_amount BIGINT       NOT NULL,
    transfer_key             VARCHAR(100) NOT NULL,
    portone_payout_id        VARCHAR(100) NULL,
    status                   VARCHAR(30)  NOT NULL,              -- PENDING / SENT / PAID / FAILED
    fail_reason              VARCHAR(255) NULL,
    processed_at             DATETIME(6)  NULL,
    deposited_at             DATETIME(6)  NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 기준일 1회 집행. 기준일 UNIQUE가 중복 편성을 막는다.
CREATE TABLE IF NOT EXISTS settlement_execution_batches
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    settlement_base_date DATE        NOT NULL,
    total_target_count   INT         NOT NULL,
    total_amount         BIGINT      NOT NULL,
    status               VARCHAR(30) NOT NULL,                   -- GENERATED / PROCESSING / COMPLETED
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 수수료율 정책. 적용 시작일마다 한 행을 쌓고 과거 행은 지우지 않는다.
-- seller_id가 NULL이면 전역 정책, 값이 있으면 그 셀러 전용이다.
CREATE TABLE IF NOT EXISTS settlement_fee_policies
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id         BIGINT        NULL,                        -- NULL이면 전역. 값 참조(seller BC)
    effective_from    DATE          NOT NULL,
    platform_fee_rate DECIMAL(5, 4) NOT NULL,
    pg_fee_rate       DECIMAL(5, 4) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


-- 전역 정책 시드. 이미 전역 정책이 있으면 아무것도 바꾸지 않는다 — 운영에서 바꾼 요율을 재실행이 되돌리지 않게 한다.
SET @global_policy_count = (SELECT COUNT(*) FROM settlement_fee_policies WHERE seller_id IS NULL);
-- 전역 정책이 없을 때만 심는다.
SET @stmt = IF(
    @global_policy_count = 0,
    'INSERT INTO settlement_fee_policies (seller_id, effective_from, platform_fee_rate, pg_fee_rate, created_at, updated_at) VALUES (NULL, ''2026-01-01'', 0.0800, 0.0280, NOW(6), NOW(6))',
    'SELECT 1'
);
PREPARE seed1_stmt FROM @stmt;
EXECUTE seed1_stmt;
DEALLOCATE PREPARE seed1_stmt;


-- ── settlements 보강 컬럼 ─────────────────────────────────────────────────────
-- ddl-auto가 만든 테이블에는 아래 7개가 없다. NOT NULL로 바로 추가하면 MySQL이 기존 행을
-- 암묵적 기본값(0·빈 문자열)으로 채워 조용히 오염되므로, NULL로 붙이고 채운 뒤 좁힌다.
-- settlements.order_id
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'order_id'
    ),
    'ALTER TABLE settlements ADD COLUMN order_id BIGINT NULL',
    'SELECT 1'
);
PREPARE col2_stmt FROM @stmt;
EXECUTE col2_stmt;
DEALLOCATE PREPARE col2_stmt;

-- settlements.shipping_fee
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'shipping_fee'
    ),
    'ALTER TABLE settlements ADD COLUMN shipping_fee BIGINT NULL',
    'SELECT 1'
);
PREPARE col3_stmt FROM @stmt;
EXECUTE col3_stmt;
DEALLOCATE PREPARE col3_stmt;

-- settlements.refund_deduction
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'refund_deduction'
    ),
    'ALTER TABLE settlements ADD COLUMN refund_deduction BIGINT NULL',
    'SELECT 1'
);
PREPARE col4_stmt FROM @stmt;
EXECUTE col4_stmt;
DEALLOCATE PREPARE col4_stmt;

-- settlements.platform_fee_rate
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'platform_fee_rate'
    ),
    'ALTER TABLE settlements ADD COLUMN platform_fee_rate DECIMAL(5, 4) NULL',
    'SELECT 1'
);
PREPARE col5_stmt FROM @stmt;
EXECUTE col5_stmt;
DEALLOCATE PREPARE col5_stmt;

-- settlements.pg_fee_rate
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'pg_fee_rate'
    ),
    'ALTER TABLE settlements ADD COLUMN pg_fee_rate DECIMAL(5, 4) NULL',
    'SELECT 1'
);
PREPARE col6_stmt FROM @stmt;
EXECUTE col6_stmt;
DEALLOCATE PREPARE col6_stmt;

-- settlements.settlement_month
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'settlement_month'
    ),
    'ALTER TABLE settlements ADD COLUMN settlement_month VARCHAR(7) NULL',
    'SELECT 1'
);
PREPARE col7_stmt FROM @stmt;
EXECUTE col7_stmt;
DEALLOCATE PREPARE col7_stmt;

-- settlements.expected_payout_date
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'expected_payout_date'
    ),
    'ALTER TABLE settlements ADD COLUMN expected_payout_date DATE NULL',
    'SELECT 1'
);
PREPARE col8_stmt FROM @stmt;
EXECUTE col8_stmt;
DEALLOCATE PREPARE col8_stmt;

-- ── 보강 컬럼 백필 ───────────────────────────────────────────────────────────
-- 금액 두 개는 0이 시작값이다.
UPDATE settlements
SET shipping_fee     = COALESCE(shipping_fee, 0),
    refund_deduction = COALESCE(refund_deduction, 0),
    updated_at       = NOW(6)
WHERE shipping_fee IS NULL
   OR refund_deduction IS NULL;

-- order_id는 주문품목 경로로 채운다. 주문품목이 사라진 행은 채우지 못하고 아래 NOT NULL 좁히기에서
-- 실패해 사람이 보게 된다 — 조용히 0을 넣는 것보다 낫다.
UPDATE settlements s
    JOIN order_items oi ON oi.id = s.order_item_id
SET s.order_id   = oi.order_id,
    s.updated_at = NOW(6)
WHERE s.order_id IS NULL;

-- 요율은 가장 먼저 적용된 전역 정책(이 마이그레이션의 시드)을 스냅샷으로 넣는다.
-- 지금 settlements에 행을 쓰는 코드가 없어 실제로는 0행을 건드리지만, 있더라도 그 행이 계산된
-- 근거는 화면에 하드코딩돼 있던 그 요율뿐이다.
UPDATE settlements s
    JOIN (SELECT platform_fee_rate, pg_fee_rate
          FROM settlement_fee_policies
          WHERE seller_id IS NULL
          ORDER BY effective_from ASC, id ASC
          LIMIT 1) p
SET s.platform_fee_rate = p.platform_fee_rate,
    s.pg_fee_rate       = p.pg_fee_rate,
    s.updated_at        = NOW(6)
WHERE s.platform_fee_rate IS NULL
   OR s.pg_fee_rate IS NULL;

-- 정산월과 지급 예정일은 저장된 생성 시각에서 뽑는다. 쓰기 시점부터는 애플리케이션이 구매확정일(KST)로 확정해 넣는다.
UPDATE settlements
SET settlement_month     = COALESCE(settlement_month, DATE_FORMAT(created_at, '%Y-%m')),
    expected_payout_date = COALESCE(expected_payout_date, DATE_ADD(DATE(created_at), INTERVAL 5 DAY)),
    updated_at           = NOW(6)
WHERE settlement_month IS NULL
   OR expected_payout_date IS NULL;

-- settlements.order_id 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'order_id' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN order_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE nn9_stmt FROM @stmt;
EXECUTE nn9_stmt;
DEALLOCATE PREPARE nn9_stmt;

-- settlements.shipping_fee 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'shipping_fee' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN shipping_fee BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE nn10_stmt FROM @stmt;
EXECUTE nn10_stmt;
DEALLOCATE PREPARE nn10_stmt;

-- settlements.refund_deduction 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'refund_deduction' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN refund_deduction BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE nn11_stmt FROM @stmt;
EXECUTE nn11_stmt;
DEALLOCATE PREPARE nn11_stmt;

-- settlements.platform_fee_rate 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'platform_fee_rate' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN platform_fee_rate DECIMAL(5, 4) NOT NULL',
    'SELECT 1'
);
PREPARE nn12_stmt FROM @stmt;
EXECUTE nn12_stmt;
DEALLOCATE PREPARE nn12_stmt;

-- settlements.pg_fee_rate 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'pg_fee_rate' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN pg_fee_rate DECIMAL(5, 4) NOT NULL',
    'SELECT 1'
);
PREPARE nn13_stmt FROM @stmt;
EXECUTE nn13_stmt;
DEALLOCATE PREPARE nn13_stmt;

-- settlements.settlement_month 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'settlement_month' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN settlement_month VARCHAR(7) NOT NULL',
    'SELECT 1'
);
PREPARE nn14_stmt FROM @stmt;
EXECUTE nn14_stmt;
DEALLOCATE PREPARE nn14_stmt;

-- settlements.expected_payout_date 좁히기 — 남은 NULL이 있으면 여기서 실패한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'expected_payout_date' AND IS_NULLABLE = 'YES'
    ),
    'ALTER TABLE settlements MODIFY COLUMN expected_payout_date DATE NOT NULL',
    'SELECT 1'
);
PREPARE nn15_stmt FROM @stmt;
EXECUTE nn15_stmt;
DEALLOCATE PREPARE nn15_stmt;

-- ── enum 컬럼 VARCHAR 수렴 ───────────────────────────────────────────────────
-- Hibernate는 @Enumerated(STRING)을 MySQL 네이티브 enum으로 만들지만 이 레포의 마이그레이션은
-- VARCHAR을 쓴다. 네이티브 enum으로 남겨 두면 코드에 상태값이 추가될 때 그 DB의 쓰기가 실패한다
-- (settlements.status에 ON_HOLD를 더하는 이 PR이 바로 그 경우다).
-- settlements.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE settlements MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en16_stmt FROM @stmt;
EXECUTE en16_stmt;
DEALLOCATE PREPARE en16_stmt;

-- payouts.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE payouts MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en17_stmt FROM @stmt;
EXECUTE en17_stmt;
DEALLOCATE PREPARE en17_stmt;

-- settlement_execution_batches.status
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_execution_batches'
          AND COLUMN_NAME = 'status' AND DATA_TYPE = 'enum'
    ),
    'ALTER TABLE settlement_execution_batches MODIFY COLUMN status VARCHAR(30) NOT NULL',
    'SELECT 1'
);
PREPARE en18_stmt FROM @stmt;
EXECUTE en18_stmt;
DEALLOCATE PREPARE en18_stmt;

-- ── ddl-auto가 걸어 둔 FK 제거 ───────────────────────────────────────────────
-- 엔티티 @JoinColumn에 NO_CONSTRAINT가 없던 동안 Hibernate가 FK를 만들었다. 이 레포는
-- 같은 BC의 부모-자식도 FK 없이 인덱스만 두기로 했고(V21 규약), 엔티티 선언도 이 PR에서 맞췄다.
-- 제약명은 Hibernate가 해시로 짓기 때문에 information_schema에서 찾아 떼어낸다.
SET @settlements_payout_fk = (SELECT CONSTRAINT_NAME
                              FROM information_schema.KEY_COLUMN_USAGE
                              WHERE TABLE_SCHEMA = DATABASE()
                                AND TABLE_NAME = 'settlements'
                                AND COLUMN_NAME = 'payout_id'
                                AND REFERENCED_TABLE_NAME IS NOT NULL
                              LIMIT 1);
SET @stmt = IF(
    @settlements_payout_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE settlements DROP FOREIGN KEY `', @settlements_payout_fk, '`')
);
PREPARE fk1_stmt FROM @stmt;
EXECUTE fk1_stmt;
DEALLOCATE PREPARE fk1_stmt;

SET @payouts_batch_fk = (SELECT CONSTRAINT_NAME
                         FROM information_schema.KEY_COLUMN_USAGE
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'payouts'
                           AND COLUMN_NAME = 'batch_id'
                           AND REFERENCED_TABLE_NAME IS NOT NULL
                         LIMIT 1);
SET @stmt = IF(
    @payouts_batch_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE payouts DROP FOREIGN KEY `', @payouts_batch_fk, '`')
);
PREPARE fk2_stmt FROM @stmt;
EXECUTE fk2_stmt;
DEALLOCATE PREPARE fk2_stmt;

-- ── 인덱스·UNIQUE 수렴 ───────────────────────────────────────────────────────
-- 빈 DB든 ddl-auto가 만든 DB든 같은 경로를 타게 하려고 CREATE TABLE에 인라인으로 두지 않고
-- 전부 조건부 ALTER로 붙인다. 판정은 이름 존재 여부다.
-- settlements.uk_settlements_idempotency_key — 같은 주문품목으로 원장을 두 번 만드는 것을 막는 방어선
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'uk_settlements_idempotency_key'
    ),
    'ALTER TABLE settlements ADD CONSTRAINT uk_settlements_idempotency_key UNIQUE (idempotency_key)',
    'SELECT 1'
);
PREPARE ix19_stmt FROM @stmt;
EXECUTE ix19_stmt;
DEALLOCATE PREPARE ix19_stmt;

-- settlements.idx_settlements_seller_id_month — 셀러 정산 목록의 월 필터
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'idx_settlements_seller_id_month'
    ),
    'ALTER TABLE settlements ADD INDEX idx_settlements_seller_id_month (seller_id, settlement_month)',
    'SELECT 1'
);
PREPARE ix20_stmt FROM @stmt;
EXECUTE ix20_stmt;
DEALLOCATE PREPARE ix20_stmt;

-- settlements.idx_settlements_order_id — 주문 단위 집계
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'idx_settlements_order_id'
    ),
    'ALTER TABLE settlements ADD INDEX idx_settlements_order_id (order_id)',
    'SELECT 1'
);
PREPARE ix21_stmt FROM @stmt;
EXECUTE ix21_stmt;
DEALLOCATE PREPARE ix21_stmt;

-- settlements.idx_settlements_status — 집행 배치의 지급 대상 스캔
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'idx_settlements_status'
    ),
    'ALTER TABLE settlements ADD INDEX idx_settlements_status (status)',
    'SELECT 1'
);
PREPARE ix22_stmt FROM @stmt;
EXECUTE ix22_stmt;
DEALLOCATE PREPARE ix22_stmt;

-- settlements.idx_settlements_payout_id — 지급 묶음에 속한 정산 조회 — FK를 떼면서 보조 인덱스를 대체한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'idx_settlements_payout_id'
    ),
    'ALTER TABLE settlements ADD INDEX idx_settlements_payout_id (payout_id)',
    'SELECT 1'
);
PREPARE ix23_stmt FROM @stmt;
EXECUTE ix23_stmt;
DEALLOCATE PREPARE ix23_stmt;

-- payouts.uk_payouts_transfer_key — 배치 재실행이 같은 셀러에게 지급을 두 번 만드는 것을 막는 방어선
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts' AND INDEX_NAME = 'uk_payouts_transfer_key'
    ),
    'ALTER TABLE payouts ADD CONSTRAINT uk_payouts_transfer_key UNIQUE (transfer_key)',
    'SELECT 1'
);
PREPARE ix24_stmt FROM @stmt;
EXECUTE ix24_stmt;
DEALLOCATE PREPARE ix24_stmt;

-- payouts.idx_payouts_seller_id_status — 셀러별 지급 이력
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts' AND INDEX_NAME = 'idx_payouts_seller_id_status'
    ),
    'ALTER TABLE payouts ADD INDEX idx_payouts_seller_id_status (seller_id, status)',
    'SELECT 1'
);
PREPARE ix25_stmt FROM @stmt;
EXECUTE ix25_stmt;
DEALLOCATE PREPARE ix25_stmt;

-- payouts.idx_payouts_batch_id — 배치에 속한 지급 조회 — FK를 떼면서 보조 인덱스를 대체한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payouts' AND INDEX_NAME = 'idx_payouts_batch_id'
    ),
    'ALTER TABLE payouts ADD INDEX idx_payouts_batch_id (batch_id)',
    'SELECT 1'
);
PREPARE ix26_stmt FROM @stmt;
EXECUTE ix26_stmt;
DEALLOCATE PREPARE ix26_stmt;

-- settlement_execution_batches.uk_settlement_execution_batches_base_date — 같은 기준일로 배치를 두 번 편성하는 것을 막는 방어선
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_execution_batches'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_execution_batches' AND INDEX_NAME = 'uk_settlement_execution_batches_base_date'
    ),
    'ALTER TABLE settlement_execution_batches ADD CONSTRAINT uk_settlement_execution_batches_base_date UNIQUE (settlement_base_date)',
    'SELECT 1'
);
PREPARE ix27_stmt FROM @stmt;
EXECUTE ix27_stmt;
DEALLOCATE PREPARE ix27_stmt;

-- settlement_fee_policies.uk_settlement_fee_policies_seller_effective — 셀러·적용시작일 조합 중복 방지. 전역 정책은 seller_id가 NULL이라 이 UNIQUE가 중복을 막지 못한다 — 조회가 정렬로 한 행을 고정한다
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_fee_policies'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_fee_policies' AND INDEX_NAME = 'uk_settlement_fee_policies_seller_effective'
    ),
    'ALTER TABLE settlement_fee_policies ADD CONSTRAINT uk_settlement_fee_policies_seller_effective UNIQUE (seller_id, effective_from)',
    'SELECT 1'
);
PREPARE ix28_stmt FROM @stmt;
EXECUTE ix28_stmt;
DEALLOCATE PREPARE ix28_stmt;

-- 세션 설정 복원. 실패로 중단되면 이 줄에 닿지 않지만, 그때는 부팅이 멈춰 커넥션도 쓰이지 않는다.
SET SESSION sql_mode = @saved_sql_mode;
