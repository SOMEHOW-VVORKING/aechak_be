-- SNS 차단 관계. 한 쌍(blocker, blocked)에 행 하나이고 켜기는 INSERT IGNORE, 끄기는 DELETE다.
-- blocker_id, blocked_id는 users.id 값 참조라 FK 없음(레포 규약). created_at/updated_at은 어댑터의 네이티브 삽입이 NOW(6)로 채운다.

CREATE TABLE IF NOT EXISTS sns_blocks
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id BIGINT      NOT NULL,
    blocked_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_block_pair UNIQUE (blocker_id, blocked_id),
    -- 내 차단 관리 목록(최근순 keyset)
    INDEX ix_sns_block_blocker (blocker_id, id),
    -- 나를 차단한 사용자 조회
    INDEX ix_sns_block_blocked (blocked_id, blocker_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
