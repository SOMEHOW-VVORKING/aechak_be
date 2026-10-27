-- SNS 팔로우 관계. 한 쌍(follower, followee)에 행 하나이고 켜기는 INSERT IGNORE, 끄기는 DELETE다.
-- follower_id, followee_id는 users.id 값 참조라 FK 없음(레포 규약). created_at/updated_at은 어댑터의 네이티브 삽입이 NOW(6)로 채운다.

CREATE TABLE IF NOT EXISTS sns_follows
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    follower_id BIGINT      NOT NULL,
    followee_id BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_follow_pair UNIQUE (follower_id, followee_id),
    -- 팔로워 목록(최근 팔로우순 keyset)
    INDEX ix_sns_follow_followee (followee_id, id, follower_id),
    -- 팔로잉 목록(최근 팔로우순 keyset)
    INDEX ix_sns_follow_follower (follower_id, id, followee_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
