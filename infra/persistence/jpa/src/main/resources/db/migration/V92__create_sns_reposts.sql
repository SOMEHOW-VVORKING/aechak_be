-- SNS 리포스트. 한 쌍(리포스트한 사람, 원글)에 행 하나이고 켜기는 INSERT IGNORE, 끄기는 DELETE다.
-- post_id는 sns_posts.id, reposter_id는 users.id 값 참조라 FK 없음. FK가 있으면 리포스트 삽입이 원글 행을 잠근다.
-- created_at/updated_at은 어댑터가 앱 시각 인자로 채운다. 팔로잉 피드가 게시 시각과 리포스트 시각을 함께 정렬하기 때문이다.

CREATE TABLE IF NOT EXISTS sns_reposts
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    post_id     BIGINT      NOT NULL,
    reposter_id BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_reposts_reposter_post UNIQUE (reposter_id, post_id),
    -- 리포스트 탭 keyset과 팔로잉 피드 원천. 끝의 created_at은 테이블을 다시 읽지 않게 한다
    INDEX ix_sns_reposts_reposter (reposter_id, id, post_id, created_at),
    -- 리포스트 표시와 차단 때 정리
    INDEX ix_sns_reposts_post (post_id, reposter_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
