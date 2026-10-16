-- 결제 대사 기록. 회차(runs)는 (소스, 기준일)당 1행 — 같은 날을 다시 돌려도 완료된 회차는 건너뛴다.
-- 건(items)은 회차 안에서 결제 식별자당 1행 — 재실행은 비교 결과만 덮어쓴다.
-- run_id·payment_id·order_group_id는 값 참조라 FK 없음(레포 규약). created_at/updated_at은 BaseEntity가 채움.
CREATE TABLE IF NOT EXISTS payment_reconciliation_runs
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    source                VARCHAR(20)   NOT NULL,              -- PORTONE / TOSS
    base_date             DATE          NOT NULL,              -- 대사 기준일(KST)
    window_from           DATETIME(6)   NOT NULL,              -- PG 상태 변경 시각 창 시작(포함)
    window_until          DATETIME(6)   NOT NULL,              -- 창 끝(미포함)
    status                VARCHAR(20)   NOT NULL,              -- RUNNING / COMPLETED / FAILED
    attempt               INT           NOT NULL,              -- 실행 횟수. 죽은 회차를 이어받을 때 1씩 증가
    fetched_count         INT           NOT NULL,              -- PG가 돌려준 건수
    compared_count        INT           NOT NULL,              -- 대조한 건수(우리만 있는 건 포함)
    matched_count         INT           NOT NULL,
    auto_resolved_count   INT           NOT NULL,
    manual_required_count INT           NOT NULL,
    failed_count          INT           NOT NULL,              -- 자동 보정이 실패한 건수. 다음 회차 lookback이 다시 본다
    started_at            DATETIME(6)   NOT NULL,
    finished_at           DATETIME(6)   NULL,
    error_message         VARCHAR(1000) NULL,
    version               INT           NOT NULL,              -- 낙관적 락. 죽은 회차 이어받기 경쟁의 심판
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_reconciliation_runs_source_base_date UNIQUE (source, base_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_reconciliation_items
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    run_id                 BIGINT       NOT NULL,
    source                 VARCHAR(20)  NOT NULL,
    payment_key            VARCHAR(255) NOT NULL,              -- PG 결제 식별자 = payments.payment_id = order_groups.public_id
    payment_id             BIGINT       NULL,                  -- 우리 payments.id. PG에만 있는 건이면 NULL
    order_group_id         BIGINT       NULL,
    our_payment_status     VARCHAR(30)  NULL,                  -- 대조 시점 스냅샷. 사후 추적용
    our_group_status       VARCHAR(30)  NULL,
    our_group_final_amount BIGINT       NULL,
    our_target_amount      BIGINT       NULL,
    our_paid_amount        BIGINT       NULL,
    our_cancellable_amount BIGINT       NULL,
    pg_status              VARCHAR(30)  NULL,                  -- 우리 어휘로 번역한 PG 상태. PG에 없으면 NULL
    pg_paid_amount         BIGINT       NULL,
    pg_cancelled_amount    BIGINT       NULL,
    pg_tx_id               VARCHAR(255) NULL,
    pg_status_changed_at   DATETIME(6)  NULL,
    mismatch_type          VARCHAR(40)  NOT NULL,
    resolution             VARCHAR(30)  NOT NULL,              -- NOT_REQUIRED / AUTO_CONFIRMED / MANUAL_REQUIRED / FAILED
    resolution_note        VARCHAR(500) NULL,
    resolved_at            DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_reconciliation_items_run_payment_key UNIQUE (run_id, payment_key),
    INDEX idx_payment_reconciliation_items_resolution (resolution, mismatch_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 대사의 "우리만 있는 건" 탐지가 창 안에서 상태가 바뀐 결제를 updated_at으로 찾는다. 결제가 쌓이면 이 조회가 먼저 느려진다.
CREATE INDEX idx_payments_updated_at ON payments (updated_at);
