-- SNS 저장. 한 쌍(저장한 사람, 게시물)에 행 하나이고 켜기는 INSERT ... ON DUPLICATE KEY UPDATE, 끄기는 DELETE다.
-- user_id는 users.id, post_id는 sns_posts.id 값 참조라 FK 없음. 저장 수는 어디에도 두지 않는다.
-- created_at/updated_at은 어댑터의 네이티브 삽입이 NOW(6)로 채운다. 저장됨 탭은 행 id로 정렬한다.

CREATE TABLE IF NOT EXISTS sns_saves
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    post_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_saves_user_post UNIQUE (user_id, post_id),
    -- 저장됨 탭(최근 저장순 keyset)
    INDEX ix_sns_saves_user (user_id, id, post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
