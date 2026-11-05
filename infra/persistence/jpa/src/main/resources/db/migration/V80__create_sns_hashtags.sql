-- SNS 해시태그. name은 정규화한 표기이고 post_count는 그 태그가 달린 게시물 수다.
-- 행은 어댑터의 네이티브 upsert가 게시할 때 만들고 수가 0이 돼도 지우지 않는다.
-- name은 utf8mb4_bin으로 바이트 그대로 비교한다. 기본 콜레이션은 가와 ㄱㅏ를 같은 이름으로 봐서 UNIQUE가 서로 다른 태그를 막는다.
-- ix_sns_hashtags_post_count는 post_count DESC, name ASC 정렬을 filesort 없이 읽으려고 post_count만 내림차순으로 둔다.

CREATE TABLE IF NOT EXISTS sns_hashtags
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(30) COLLATE utf8mb4_bin NOT NULL,
    post_count INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_hashtags_name UNIQUE (name),
    INDEX ix_sns_hashtags_post_count (post_count DESC, name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
