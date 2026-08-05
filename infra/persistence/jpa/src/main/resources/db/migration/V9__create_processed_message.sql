CREATE TABLE processed_message
(
    consumer     VARCHAR(96) NOT NULL,                              -- 컨슈머(그룹) 이름. 같은 메시지도 컨슈머마다 별도 처리
    event_id     VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,         -- 멱등키(엔벨로프 eventId). bin = 바이트 정확 비교
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (consumer, event_id)                                -- 복합 PK = 중복 차단
);
