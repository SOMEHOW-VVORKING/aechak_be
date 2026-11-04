-- SNS 게시물. caption은 금지어를 가린 뒤의 글이고 게시물을 지우면 NULL이 된다.
-- author_id, pet_id는 users.id, pet_profiles.id 값 참조라 FK 없음(레포 규약).
-- 같은 작성자의 같은 Idempotency-Key 게시는 uk_sns_posts_author_idempotency가 한 행으로 막는다.
-- idempotency_key는 utf8mb4_bin으로 바이트 그대로 비교한다. 기본 콜레이션이면 대소문자만 다른 키가 같은 게시물로 묶인다.

CREATE TABLE IF NOT EXISTS sns_posts
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    public_id       VARCHAR(26)   NOT NULL,
    author_id       BIGINT        NOT NULL,
    caption         VARCHAR(2200) NULL,
    media_type      VARCHAR(10)   NOT NULL,
    pet_id          BIGINT        NULL,
    status          VARCHAR(20)   NOT NULL,
    deletion_reason VARCHAR(20)   NULL,
    idempotency_key VARCHAR(100)  COLLATE utf8mb4_bin NOT NULL,
    edited_at       DATETIME(6)   NULL,
    deleted_at      DATETIME(6)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_posts_public_id UNIQUE (public_id),
    CONSTRAINT uk_sns_posts_author_idempotency UNIQUE (author_id, idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
