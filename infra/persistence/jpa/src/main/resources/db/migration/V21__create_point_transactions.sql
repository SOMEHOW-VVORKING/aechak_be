-- 적립금 원장(point_transactions). 잔액(users.point_balance)은 파생 캐시고 SoT는 이 원장.
-- buyer_id는 같은 BC(user) 참조지만 팀 컨벤션대로 FK 없이 인덱스만. created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.
-- 멱등키 UNIQUE가 원장 이중 기록의 최후 방어선 — 주문 사용분은 USE:ORDER:{주문그룹 publicId}로 결정적이라 재실행돼도 1행.
CREATE TABLE IF NOT EXISTS point_transactions
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id         BIGINT       NOT NULL,
    amount           BIGINT       NOT NULL,               -- 항상 양수. 방향(차감/적립)은 transaction_type이 정함
    transaction_type VARCHAR(20)  NOT NULL,               -- LOCK / USE / RELEASE / EARN
    source_type      VARCHAR(30)  NULL,                   -- 다형 참조 유형 (예: ORDER_GROUP)
    source_id        BIGINT       NULL,                   -- 다형 참조 id — 유형이 여럿이라 FK 불가
    idempotency_key  VARCHAR(100) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_point_transactions_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_point_transactions_buyer_id (buyer_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
