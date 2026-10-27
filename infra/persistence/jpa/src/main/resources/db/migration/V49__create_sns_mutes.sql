-- SNS 뮤트 관계. 한 쌍(muter, muted)에 행 하나이고 켜기는 INSERT IGNORE, 끄기는 DELETE다.
-- muter_id, muted_id는 users.id 값 참조라 FK 없음(레포 규약). created_at/updated_at은 어댑터의 네이티브 삽입이 NOW(6)로 채운다.

CREATE TABLE IF NOT EXISTS sns_mutes
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    muter_id   BIGINT      NOT NULL,
    muted_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_mute_pair UNIQUE (muter_id, muted_id),
    -- 내 뮤트 관리 목록(최근순 keyset)
    INDEX ix_sns_mute_muter (muter_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
