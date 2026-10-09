-- 결제 준비(사전등록) 저장소. 주문그룹 1건당 결제 1행.
-- order_group_id는 BC 밖 참조라 값 참조로 두고 FK 없음.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.
-- ERD의 environment는 두지 않음. 환경마다 DB가 갈려 한 DB에 TEST와 LIVE가 섞일 일이 없기 때문임.
CREATE TABLE IF NOT EXISTS payments
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    order_group_id     BIGINT       NOT NULL,              -- 값 참조(order BC)
    payment_id         VARCHAR(255) NOT NULL,              -- 포트원 결제 식별자. 지금은 주문그룹 public_id를 그대로 씀
    transaction_id     VARCHAR(255) NULL,                  -- PG 거래 식별자. 승인 전 NULL, MySQL은 NULL 중복을 UNIQUE로 안 막음
    method             VARCHAR(20)  NOT NULL,              -- CARD / KAKAO_PAY / TOSS_PAY
    target_amount      BIGINT       NOT NULL,              -- 서버가 등록한 결제 예정 금액. 위변조 검증 기준
    real_paid_amount   BIGINT       NULL,                  -- PG가 알려준 실제 결제 금액. 승인 전 NULL
    status             VARCHAR(30)  NOT NULL,              -- PENDING / APPROVED / FAILED
    failure_code       VARCHAR(100) NULL,                  -- PG 원본 실패 코드
    cancellable_amount BIGINT       NULL,                  -- 남은 취소가능액. 승인 시 real_paid_amount로 초기화
    version            INT          NOT NULL,              -- 낙관적 락. 승인 쓰기가 붙으면 같은 행을 쓰는 경로가 늘어남
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_group_id UNIQUE (order_group_id),
    CONSTRAINT uk_payments_payment_id UNIQUE (payment_id),
    CONSTRAINT uk_payments_transaction_id UNIQUE (transaction_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
