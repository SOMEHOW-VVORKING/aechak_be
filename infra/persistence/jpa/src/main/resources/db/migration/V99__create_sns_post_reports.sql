-- SNS 게시물 신고. 같은 신고자는 같은 게시물을 한 번만 신고한다(UNIQUE).
-- post_id는 sns_posts.id, reporter_id는 users.id 값 참조라 FK 없음. 게시물이 지워져도 신고 행은 남는다.
-- 처리 상태는 두지 않는다. 운영자 결정은 report_decisions에 target_type SNS_POST로 남는다.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.

CREATE TABLE IF NOT EXISTS sns_post_reports
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    reason_code VARCHAR(30)  NOT NULL, -- SPAM / ABUSE / INAPPROPRIATE / FRAUD / OTHER
    reason_text VARCHAR(500) NULL,     -- 상세 내용 (OTHER면 필수, 앱 검증)
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sns_post_reports_post_reporter UNIQUE (post_id, reporter_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
