-- SNS 댓글과 대댓글. 대댓글은 parent_id로 최상위 댓글을 가리키고 대댓글 아래 대댓글은 없다.
-- 지운 댓글도 행을 남긴다. content는 금지어를 가린 뒤의 글이고 작성자 삭제와 운영자 삭제면 NULL이 된다.
-- author_id는 users.id 값 참조라 FK 없음. post_id와 parent_id는 같은 BC 참조라 FK를 건다.
-- 같은 작성자가 같은 게시물에 같은 Idempotency-Key로 쓴 댓글은 uk_sns_comments_author_idempotency가 한 행으로 막는다.
-- idempotency_key는 utf8mb4_bin으로 바이트 그대로 비교한다. 기본 콜레이션이면 대소문자만 다른 키가 같은 댓글로 묶인다.

CREATE TABLE IF NOT EXISTS sns_comments
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    public_id       VARCHAR(26)  NOT NULL,
    post_id         BIGINT       NOT NULL,
    parent_id       BIGINT       NULL,
    author_id       BIGINT       NOT NULL,
    content         VARCHAR(500) NULL,
    status          VARCHAR(20)  NOT NULL,
    deletion_reason VARCHAR(20)  NULL,
    idempotency_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    edited_at       DATETIME(6)  NULL,
    deleted_at      DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_comments_public_id UNIQUE (public_id),
    CONSTRAINT uk_sns_comments_author_idempotency UNIQUE (author_id, post_id, idempotency_key),
    -- 최상위 댓글 keyset과 게시물별 수. 끝의 status, author_id로 클러스터드 인덱스를 다시 읽지 않는다
    INDEX ix_sns_comments_post_parent (post_id, parent_id, id, status, author_id),
    -- 대댓글과 안내 문구 판정
    INDEX ix_sns_comments_parent (parent_id, id, status, author_id),
    CONSTRAINT fk_sns_comments_post FOREIGN KEY (post_id) REFERENCES sns_posts (id),
    CONSTRAINT fk_sns_comments_parent FOREIGN KEY (parent_id) REFERENCES sns_comments (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
