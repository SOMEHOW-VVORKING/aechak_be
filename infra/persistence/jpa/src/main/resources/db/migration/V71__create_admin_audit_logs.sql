-- 운영자 조치 통합 기록. 더하기만 있는 테이블이다 -- 코드에 수정·삭제 경로를 만들지 않는다.
-- 번호 주의: dev 에 V68(create_report_tables)이 적용됐다. 신고 접수 PR 이 V65 에서 V68 로 재채번된
-- 직후 앞 번호들보다 먼저 머지돼서, develop 은 V61 다음이 곧 V68 이고 V62~V67 은 다시 쓸 수 없는
-- 죽은 번호다. 그래서 V69 부터 다시 받아 이 PR 은 V71 을 쓴다.
-- out-of-order가 꺼져 있어 이미 적용된 번호보다 낮은 번호를 올리면 실행 모듈이 부팅 검증에서 막힌다.
-- CI 는 빈 DB 에 전체를 순서대로 적용하므로 통과한다 -- 이 위반은 배포에서만 드러난다.
-- 이 PR을 다른 어드민 PR 뒤에 머지하면 그 시점의 dev 적용 번호를 다시 실측해 재채번해야 한다 --
-- 내용은 그대로 두고 git mv 로 파일 이름만 바꾸고, AdminAuditLogSchemaParityTest 의 MIGRATION 상수도 같이 바꾼다.
--
-- actor_user_id는 users.id 값 참조라 FK 없음(레포 규약) -- 조치한 계정이 지워져도 행은 남아야 한다.
-- target_id가 VARCHAR인 이유: 어드민 API가 대상을 가리키는 식별자를 그대로 적는데 그 형태가
-- 대상 종류마다 다르다(상품은 공개 ULID 26자, 입점 신청·관리자는 내부 숫자 id).
-- 종류별 규칙은 코드의 AdminAuditTargetType이 선언하고 기록 시점에 대조한다.
-- public_id는 레포의 다른 public_id 칼럼과 같은 VARCHAR(26)으로 둔다 -- 엔티티 선언(length=26)과
-- 어긋나면 ddl-auto로 스키마를 만드는 테스트·로컬과 운영의 칼럼 타입이 갈린다.
-- created_at/updated_at은 BaseEntity가 채운다. 갱신 경로가 없으므로 updated_at은 created_at과 영원히 같다.
CREATE TABLE IF NOT EXISTS admin_audit_logs
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    public_id     VARCHAR(26)  NOT NULL,              -- ULID. 외부 노출 식별자
    actor_user_id BIGINT       NOT NULL,              -- 조치한 운영자 users.id
    action        VARCHAR(60)  NOT NULL,              -- AdminAuditAction
    target_type   VARCHAR(40)  NOT NULL,              -- AdminAuditTargetType
    target_id     VARCHAR(64)  NOT NULL,
    target_label  VARCHAR(200) NULL,                  -- 기록 시점의 대상 이름
    reason        VARCHAR(500) NULL,                  -- 운영자가 적은 사유
    result        VARCHAR(10)  NOT NULL,              -- SUCCESS / FAILURE
    http_status   INT          NOT NULL,              -- 조치 요청이 받은 HTTP 상태
    request_id    VARCHAR(64)  NULL,                  -- X-Trace-Id. 서버 로그 상관관계 키
    client_ip     VARCHAR(45)  NULL,                  -- IPv6 전개형까지 들어가는 길이
    acted_at      DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_audit_logs_public_id UNIQUE (public_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 아래 인덱스 셋을 엔티티에도 같은 이름·같은 칼럼 순서로 선언한다. 한쪽만 두면 ddl-auto로
-- 스키마를 만드는 테스트·로컬만 인덱스 없이 돌아 조회 계획이 환경 간에 갈린다(V52의 규약과 동일).

-- 기본 목록(최근순 keyset)과 기간 필터
CREATE INDEX ix_admin_audit_logs_recent
    ON admin_audit_logs (acted_at, id);

-- "이 운영자가 지난주에 무엇을 했나"
CREATE INDEX ix_admin_audit_logs_actor
    ON admin_audit_logs (actor_user_id, id);

-- "이 대상에게 누가 무슨 조치를 했나"
CREATE INDEX ix_admin_audit_logs_target
    ON admin_audit_logs (target_type, target_id, id);
