-- SNS 댓글 신고. 같은 신고자는 같은 댓글을 한 번만 신고한다(UNIQUE). 대댓글도 그 대댓글 행을 가리킨다.
-- comment_id는 sns_comments.id, reporter_id는 users.id 값 참조라 FK 없음. 댓글이 지워져도 신고 행은 남는다.
-- 처리 상태는 두지 않는다. 운영자 결정은 report_decisions에 target_type SNS_COMMENT로 남는다.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.

CREATE TABLE IF NOT EXISTS sns_comment_reports
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    comment_id  BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    reason_code VARCHAR(30)  NOT NULL, -- SPAM / ABUSE / INAPPROPRIATE / FRAUD / OTHER
    reason_text VARCHAR(500) NULL,     -- 상세 내용 (OTHER면 필수, 앱 검증)
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_comment_reports_comment_reporter UNIQUE (comment_id, reporter_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
