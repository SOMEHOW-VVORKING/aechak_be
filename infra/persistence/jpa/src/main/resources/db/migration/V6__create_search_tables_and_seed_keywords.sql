-- 검색 도메인 기반: 최근 검색어·추천 검색어 테이블 (SCRUM-149).
-- recent_searches: (user_id, normalized_keyword) 유일 제약으로 중복 키워드를 한 행으로 접고,
--                  (user_id, searched_at DESC) 인덱스로 사용자별 최신순 조회를 커버한다. 하드 삭제.
-- recommended_keywords: 노출용 고정 마스터. is_active=true를 sort_order 순으로 노출.
-- [번호] V6 채번. develop의 최신이 V5(SCRUM-77)이고 V3와 V4는 SCRUM-143(outbox, processed_message)가 선점해 max+1로 정했다.

CREATE TABLE IF NOT EXISTS recent_searches
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    normalized_keyword VARCHAR(255) NOT NULL,
    display_keyword    VARCHAR(255) NOT NULL,
    searched_at        DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recent_searches_user_normalized UNIQUE (user_id, normalized_keyword),
    INDEX idx_recent_searches_user_searched_at (user_id, searched_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS recommended_keywords
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    keyword    VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL,
    is_active  TINYINT(1)   NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
