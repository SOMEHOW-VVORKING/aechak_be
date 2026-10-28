-- 셀러 리뷰 답글 스키마.
-- prod는 ddl-auto=none이라 스키마를 여기서 보장하고, dev에는 Hibernate가 만든 동명 테이블이 있을 수 있어
-- 생성은 IF NOT EXISTS로 가드한다(V15·V27과 같은 규약).
--
-- 작성 규약(V27)
--  - FK 없음. BC 밖 참조도 같은 BC 부모-자식도 인덱스만 둔다.
--    리뷰 삭제 시 답글 정리는 애플리케이션 트랜잭션이 책임진다 — 리뷰는 소프트 삭제라 FK CASCADE가 애초에 돌지 않는다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 일치시킨다.

CREATE TABLE IF NOT EXISTS review_replies
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    review_id  BIGINT       NOT NULL,
    seller_id  BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    -- 리뷰 하나에 답글 하나. 선조회와 저장 사이 경쟁의 최종 판정자이기도 하다.
    CONSTRAINT uk_review_replies_review_id UNIQUE (review_id),
    INDEX ix_review_replies_seller_id (seller_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
