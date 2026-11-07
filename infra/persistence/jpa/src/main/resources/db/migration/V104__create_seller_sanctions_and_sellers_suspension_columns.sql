-- 어드민 셀러 판매정지: 제재 이력 테이블과 sellers의 해제 예정 시각·운영자 메모 추가.
--
-- 작성 규약(V27·V58과 동일)
--  - FK 없음. BC 밖 참조도 같은 BC 참조도 인덱스만 둔다.
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다.
--  - 인덱스 이름은 엔티티 @Table 선언과 일치시킨다.
--
-- 컬럼 추가를 조건부 ALTER로 쓰는 이유: dev는 Flyway와 ddl-auto(update)가 함께 돌아 엔티티 선언을
-- 본 Hibernate가 같은 컬럼을 먼저 만들어 둔 DB가 있을 수 있다. 그 상태에서 맨 ALTER를 내보내면
-- 중복 컬럼으로 실패하고, 한 번 실패한 파일은 그 뒤로 영구 미적용으로 남는다(V55와 같은 수렴 방식).
-- sellers 테이블 자체의 존재는 V27이 보장하므로 테이블 조건은 두지 않는다.

SET @saved_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 5;

-- 1. 판매정지 이력. 추가 전용 기록이라 UPDATE가 없다 — 해제도 action=RELEASE 행 추가로 남긴다
--    (user_sanctions와 같은 모양). reason_code가 NULL인 행은 해제다: 해제는 제재 사유 어휘를
--    쓰지 않고 상세 문장만 받는다.
--    seller_id는 sellers.user_id다 — sellers는 user_id가 PK이고 별도 셀러 식별자가 없다.
CREATE TABLE IF NOT EXISTS seller_sanctions
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id         BIGINT       NOT NULL,            -- 값 참조(sellers.user_id). FK 없이 인덱스만
    action            VARCHAR(30)  NOT NULL,            -- SUSPEND / RELEASE
    reason_code       VARCHAR(30)  NULL,                -- FALSE_PRODUCT_INFO / REPEATED_REPORT / ABNORMAL_TRANSACTION / OTHER. 해제는 NULL
    reason_detail     VARCHAR(500) NULL,                -- 고정 어휘로 담기지 않는 맥락. OTHER는 필수
    effective_until   DATETIME(6)  NULL,                -- 그때 정한 해제 예정 시각. 무기한 정지와 해제 행은 NULL
    memo              VARCHAR(500) NULL,                -- 조치 시점의 운영자 메모 스냅샷. 셀러에게 노출하지 않는다
    acted_by_admin_id BIGINT       NOT NULL,            -- 조치한 운영자. 분쟁 때 "누가"를 답하는 컬럼
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_seller_sanctions_seller_id (seller_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 2. sellers.suspension_until — 현재 정지의 해제 예정 시각. 목록이 행마다 보여 주는 값이라
--    이력을 뒤지지 않고 읽히는 자리에 둔다(users.sanction_until과 같은 결).
--    만료 판정 인덱스는 두지 않는다 — 자동 해제 경로가 없고 조회는 status 조건이 먼저 걸러낸다.
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'suspension_until'
    ),
    'ALTER TABLE sellers ADD COLUMN suspension_until DATETIME(6) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_suspension_until FROM @stmt;
EXECUTE add_suspension_until;
DEALLOCATE PREPARE add_suspension_until;

-- 3. sellers.admin_memo — 셀러별 운영자 내부 메모 한 칸. 화면이 제재 없이 메모만 저장하는 경로를
--    갖고 있어 현재 값을 이력에서 유도하지 않고 직접 들고 있는다(이력 행의 memo는 그 조치 시점의 사본).
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'admin_memo'
    ),
    'ALTER TABLE sellers ADD COLUMN admin_memo VARCHAR(500) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_admin_memo FROM @stmt;
EXECUTE add_admin_memo;
DEALLOCATE PREPARE add_admin_memo;

SET SESSION lock_wait_timeout = @saved_lock_wait_timeout;
