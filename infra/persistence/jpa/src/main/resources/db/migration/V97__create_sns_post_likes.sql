-- SNS 게시물 좋아요. 한 쌍(게시물, 누른 사람)에 행 하나이고 켜기는 INSERT ... ON DUPLICATE KEY UPDATE, 끄기는 DELETE다.
-- post_id는 sns_posts.id, user_id는 users.id 값 참조라 FK 없음. 좋아요 수는 저장하지 않고 볼 때 행을 센다.
-- created_at/updated_at은 어댑터의 네이티브 삽입이 NOW(6)로 채운다. 좋아요한 사람 목록은 행 id로 정렬한다.

CREATE TABLE IF NOT EXISTS sns_post_likes
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_post_likes_post_user UNIQUE (post_id, user_id),
    -- 좋아요한 사람(최근순). user_id까지 담아 차단 제외를 인덱스만 읽고 거른다
    INDEX ix_sns_post_likes_post_recent (post_id, id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
