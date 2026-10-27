-- 어드민 회원 제재: 제재 이력 테이블과 users.sanction_until 추가.
--
-- 작성 규약(V27과 동일)
--  - FK 없음. BC 밖 참조도 같은 BC 참조도 인덱스만 둔다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다.
--  - 인덱스 이름은 엔티티 @Table 선언과 일치시킨다.
--
-- users는 ddl-auto가 만드는 테이블이라 테이블이 없는 빈 DB에서는 컬럼 추가를 건너뛴다(V34와 같은 조건).

SET @saved_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 5;

-- 1. 제재 이력. 추가 전용 기록이라 UPDATE가 없다 — 해제도 action=RELEASE 행 추가로 남긴다.
--    reason_code가 NULL인 행은 해제다: 해제는 제재 사유 어휘를 쓰지 않고 상세 문장만 받는다.
CREATE TABLE IF NOT EXISTS user_sanctions
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    target_user_id     BIGINT       NOT NULL,            -- 값 참조(user BC). FK 없이 인덱스만
    action             VARCHAR(30)  NOT NULL,            -- RESTRICT / SUSPEND / BAN / FORCE_WITHDRAW / RELEASE
    reason_code        VARCHAR(30)  NULL,                -- ABUSIVE_REVIEW / CHAT_DISPUTE / FALSE_REPORT / OTHER. 해제는 NULL
    reason_detail      VARCHAR(500) NULL,                -- 고정 어휘로 담기지 않는 맥락. OTHER는 필수
    effective_until    DATETIME(6)  NULL,                -- 임시 정지의 만료 시각. 다른 조치는 NULL
    memo               VARCHAR(300) NULL,                -- 운영자 내부 메모. 회원에게 노출하지 않는다
    acted_by_admin_id  BIGINT       NOT NULL,            -- 조치한 운영자. 분쟁 때 "누가"를 답하는 컬럼
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_user_sanctions_target_user_id (target_user_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 2. users.sanction_until — 임시 정지의 만료 시각. 만료 판정은 조회 시점에 하므로 인덱스를 두지 않는다
--    (상태별 조회는 status 조건이 먼저 걸러낸다).
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
    )
    AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sanction_until'
    ),
    'ALTER TABLE users ADD COLUMN sanction_until DATETIME(6) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_sanction_until FROM @stmt;
EXECUTE add_sanction_until;
DEALLOCATE PREPARE add_sanction_until;

SET SESSION lock_wait_timeout = @saved_lock_wait_timeout;
