-- SNS 사용자별 팔로워, 팔로잉, 게시물 수. 관계 행이 실제로 바뀔 때 같은 트랜잭션에서 원자 갱신한다.
-- user_id는 users.id 값 참조라 FK 없음(레포 규약). created_at/updated_at은 어댑터의 네이티브 upsert가 NOW(6)로 채운다.

CREATE TABLE IF NOT EXISTS sns_user_stats
(
    user_id         BIGINT      NOT NULL,
    follower_count  INT         NOT NULL,
    following_count INT         NOT NULL,
    post_count      INT         NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
