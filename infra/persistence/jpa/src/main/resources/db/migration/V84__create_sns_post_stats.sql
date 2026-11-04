-- SNS 게시물별 리포스트 수. 리포스트 행이 실제로 바뀔 때 같은 트랜잭션에서 원자 갱신하고 행이 없으면 0으로 읽는다.
-- post_id는 sns_posts.id 값 참조라 FK 없음. created_at/updated_at은 어댑터의 네이티브 upsert가 NOW(6)로 채운다.

CREATE TABLE IF NOT EXISTS sns_post_stats
(
    post_id      BIGINT      NOT NULL,
    repost_count INT         NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
