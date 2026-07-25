-- 이벤트 백본: 트랜잭셔널 아웃박스 (SCRUM-143)
-- 소유: :kafka 모듈. JPA 엔티티가 아니며 JdbcClient로만 접근한다.
CREATE TABLE IF NOT EXISTS outbox_message
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,              -- 단조 증가 = 발행 FIFO 순서
    event_id        BINARY(16)   NOT NULL,                             -- 엔벨로프 UUID → Kafka 헤더 → 컨슈머 dedup 키
    aggregate_type  VARCHAR(64)  NOT NULL,                             -- 발행 BC 이름('order' 등) → 토픽 라우팅
    aggregate_id    VARCHAR(64)  NOT NULL,                             -- Kafka 파티션 키 = 애그리거트별 순서
    event_type      VARCHAR(96)  NOT NULL,                             -- ~Message 클래스 simpleName
    trace_id        VARCHAR(64)  NULL,                                 -- 발행 시 MDC traceId → Kafka 헤더 미러
    payload         JSON         NOT NULL,                             -- 엔벨로프 전체 JSON (발행 형태로 저장)
    status          TINYINT      NOT NULL DEFAULT 0,                   -- 0=PENDING 1=PUBLISHED 2=DEAD(운영 알림)
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    DATETIME(6)  NULL,                                 -- 보존·청소 기준(14일)
    attempts        INT          NOT NULL DEFAULT 0,                   -- 재시도 카운터(캡 초과 → DEAD)
    next_attempt_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),-- 백오프 게이트
    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_event_id (event_id),
    KEY idx_outbox_relay (status, next_attempt_at, id),                -- 릴레이 클레임 쿼리
    KEY idx_outbox_order (aggregate_type, aggregate_id, status, id)    -- 애그리거트별 순서가드(NOT EXISTS) 커버링
);
