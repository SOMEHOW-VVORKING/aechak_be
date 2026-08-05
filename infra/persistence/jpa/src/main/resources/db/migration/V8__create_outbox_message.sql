-- 이벤트 백본: 트랜잭셔널 아웃박스 (SCRUM-143)
-- 소유: :kafka 모듈. JPA 엔티티가 아니며 JdbcClient로만 접근한다.
CREATE TABLE outbox_message
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,              -- 단조 증가. occurred_at이 같을 때의 정렬 타이브레이커
    event_id        VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,         -- 멱등키. bin = 바이트 정확 비교
    aggregate_type  VARCHAR(64)  NOT NULL,                             -- 발행 BC 이름('order' 등) → 토픽 라우팅
    aggregate_id    VARCHAR(64)  NOT NULL,                             -- Kafka 파티션 키 = 애그리거트별 순서
    event_type      VARCHAR(96)  NOT NULL,                             -- ~Message 클래스 simpleName
    trace_id        VARCHAR(64)  NULL,                                 -- 발행 시 MDC traceId → Kafka 헤더 미러
    payload         JSON         NOT NULL,                             -- 엔벨로프 전체 JSON (발행 형태로 저장)
    occurred_at     DATETIME(6)  NOT NULL,                             -- 사건 발생 시각(메시지 소유). 배치 재발행 정렬 기준
    expired_at      DATETIME(6)  NULL,                                 -- 허용 지연 마감. NULL = 만료 없음(무기한 재시도)
    status          TINYINT      NOT NULL DEFAULT 0,                   -- 0=PENDING 1=PUBLISHED 2=DEAD 3=HOLD(수동 재개 대기)
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    DATETIME(6)  NULL,                                 -- 보존·청소 기준(14일)
    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_event_id (event_id),
    KEY idx_outbox_sweep (status, occurred_at, id)
);
