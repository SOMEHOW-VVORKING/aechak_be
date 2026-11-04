-- SNS 게시물의 사진과 영상. 게시물과 함께만 만들어지고 행은 지우지 않는다.
-- media_type은 IMAGE, VIDEO이고 영상만 thumbnail_key, duration_ms를 채운다.
-- storage_key UNIQUE는 두 게시물이 한 파일을 나눠 쓰지 못하게 한다. 나눠 쓰면 한쪽을 지울 때 다른 쪽 사진도 사라진다.

CREATE TABLE IF NOT EXISTS sns_post_media
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    sort_order    INT          NOT NULL,
    media_type    VARCHAR(10)  NOT NULL,
    storage_key   VARCHAR(512) NOT NULL,
    thumbnail_key VARCHAR(512) NULL,
    duration_ms   INT          NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_post_media_post_sort UNIQUE (post_id, sort_order),
    CONSTRAINT uk_sns_post_media_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_sns_post_media_post FOREIGN KEY (post_id) REFERENCES sns_posts (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
