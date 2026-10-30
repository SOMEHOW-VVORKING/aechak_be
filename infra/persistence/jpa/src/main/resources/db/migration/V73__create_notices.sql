-- 서비스 공지: 운영자가 쓰고 구매자·셀러가 읽는다.
--
-- 작성 규약(V27과 동일)
--  - FK 없음. BC 밖 참조도 같은 BC 참조도 인덱스만 둔다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다.
--  - 인덱스 이름은 엔티티 @Table 선언과 일치시킨다.
--
-- 예약 게시(SCHEDULED)와 발송(푸시·메일) 컬럼은 두지 않는다. 전이시킬 배치도, 조립된 발송
-- 어댑터도 없어서 값이 영원히 움직이지 않는 컬럼이 된다. 둘 중 하나가 생기는 PR이 컬럼을 더한다.

CREATE TABLE IF NOT EXISTS notices
(
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    public_id                 VARCHAR(26)  NOT NULL,            -- 외부 노출 식별자(ULID). 경로는 전부 이 값으로 받는다
    title                     VARCHAR(200) NOT NULL,
    content                   TEXT         NOT NULL,            -- 최대 2000자(애그리거트가 검증)
    category                  VARCHAR(30)  NOT NULL,            -- SERVICE / MAINTENANCE / POLICY / EVENT / SETTLEMENT
    audience                  VARCHAR(20)  NOT NULL,            -- ALL / BUYER / SELLER. 독자를 가르는 유일한 축
    status                    VARCHAR(20)  NOT NULL,            -- DRAFT / PUBLISHED / UNPUBLISHED
    pinned                    BIT(1)       NOT NULL,            -- 읽는 쪽 목록 상단 고정. BOOLEAN(=tinyint)으로 두면 Hibernate가 만드는 bit(1)과 갈린다
    published_at              DATETIME(6)  NULL,                -- 가장 최근 게시 시각. 미게시는 NULL
    internal_memo             VARCHAR(300) NULL,                -- 운영자 내부 메모. 읽는 쪽 응답에 싣지 않는다
    created_by_admin_id       BIGINT       NOT NULL,            -- 초안을 만든 운영자. 값 참조(user BC), FK 없이 인덱스도 두지 않는다
    last_modified_by_admin_id BIGINT       NULL,                -- 수정·게시·내림을 마지막으로 한 운영자
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notices_public_id (public_id),
    -- 읽는 쪽 목록: status·audience로 걸러 (pinned, published_at, id) 내림차순으로 읽는다.
    -- 정렬 세 키를 꼬리에 둬 커버링 + 동시각 타이브레이크.
    INDEX idx_notices_status_audience_pinned_published_at (status, audience, pinned, published_at, id),
    -- 어드민 목록: 상태 필터 + 최근 작성순
    INDEX idx_notices_status_created_at (status, created_at, id),
    -- 상태 필터가 없는 어드민 전체 목록용 — PK는 created_at 정렬에 쓸 수 없다
    INDEX idx_notices_created_at_id (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
