-- SNS 게시물에 단 해시태그. sort_order는 작성자가 단 순서다.
-- hashtag_id는 sns_hashtags.id 값 참조라 FK가 없다. 엔티티도 태그 행과 연관 없이 id만 든다.
-- uk_sns_post_hashtags_post_hashtag는 한 게시물에 같은 태그가 두 번 달리지 못하게 한다.

CREATE TABLE IF NOT EXISTS sns_post_hashtags
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    hashtag_id BIGINT      NOT NULL,
    sort_order INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_post_hashtags_post_hashtag UNIQUE (post_id, hashtag_id),
    CONSTRAINT fk_sns_post_hashtags_post FOREIGN KEY (post_id) REFERENCES sns_posts (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
