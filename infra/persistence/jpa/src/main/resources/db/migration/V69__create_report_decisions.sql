-- 운영자 신고 처리 결정. 누가·언제·어느 신고를·무슨 근거로 어떻게 처리했는지를 남긴다.
--
-- 신고 엔티티(product_reports·review_reports·user_reports)의 status 컬럼을 쓰지 않는 이유:
-- 세 표의 상태 어휘가 서로 다르고(RECEIVED/FORWARDED, PENDING/RESOLVED/REJECTED,
-- RECEIVED/FORWARDED/RESOLVED/DISMISSED) 운영자 목록은 그 셋을 한 필터로 걸러야 한다.
-- 또 상태값 하나로는 "왜 그렇게 결정했는가"와 "누가 했는가"가 사라져 분쟁에서 근거로 쓸 수 없다.
-- V54(claim_interventions)와 같은 판단이다 — 운영자 결정은 애그리거트가 아니라 별도 표에 남긴다.
--
-- 작성 규약(V54·V58과 동일)
--  - FK 없음. BC 밖 참조도 인덱스만 둔다. 여기는 한 컬럼이 세 BC의 서로 다른 표를 가리켜
--    애초에 FK로 표현할 수 없다(target_type이 어느 표인지를 가린다).
--  - created_at/updated_at은 BaseEntity가 채우므로 DB 기본값을 두지 않는다.
--  - enum 컬럼은 VARCHAR로 둔다.
--  - 인덱스·UNIQUE 이름은 엔티티 @Table 선언과 일치시킨다(ReportDecision.UK_TARGET).
--
-- 번호: V69. 이 표가 읽는 product_reports·user_reports 를 Flyway 로 올리는 것이 신고 접수(#100)라
-- 그 PR 뒤에 와야 하고, #100 이 V68 로 비켜 왔으므로 그보다 한 칸 뒤를 쓴다.
-- 두 번 옮겼다. 처음 V64 는 측정 시점이 #168 의 재채번(V56->V64)보다 빨라 겹쳤고,
-- 다음 V68 은 #100 이 그 자리로 오면서 다시 겹쳤다. 내용은 두 번 다 손대지 않고 이름만 바꿨다.
-- 같은 번호 둘이 develop 에 들어가면 Found more than one migration with version 으로 부팅이 거부된다.

CREATE TABLE IF NOT EXISTS report_decisions
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    target_type          VARCHAR(20)  NOT NULL COMMENT '신고 대상 종류 PRODUCT/REVIEW/USER — report_id가 어느 표의 id인지를 가린다',
    report_id            BIGINT       NOT NULL COMMENT 'target_type이 가리키는 신고 표의 id 값 참조',
    decision             VARCHAR(30)  NOT NULL COMMENT '처리 결정 DISMISS/HIDE_TARGET/SANCTION_ACCOUNT',
    reason_code          VARCHAR(30)  NOT NULL COMMENT '처리 사유 고정 어휘 FALSE_AD/ABUSE/EXTERNAL_TRADE/ILLEGAL/FALSE_REPORT/NOT_CONFIRMED',
    reason_detail        VARCHAR(500) NULL COMMENT '고정 어휘로 담기지 않는 판정 맥락',
    notice_to_owner      VARCHAR(300) NULL COMMENT '대상 소유자에게 전달할 안내 문구. 통보 경로가 아직 없어 문구만 보관한다',
    internal_memo        VARCHAR(300) NULL COMMENT '운영 내부 메모. 소유자·신고자에게 내려보내지 않는다',
    target_owner_user_id BIGINT       NOT NULL COMMENT '처리 시점의 대상 소유자 users.id 값 참조 — 상품은 셀러, 리뷰는 작성자, 사용자는 본인',
    acted_by_admin_id    BIGINT       NOT NULL COMMENT '결정한 운영자 users.id 값 참조. 분쟁 때 "누가"를 답하는 컬럼',
    created_at           DATETIME(6)  NOT NULL COMMENT '결정 시각',
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 한 신고는 한 번만 처리된다. 응용 계층의 중복 검사가 동시 요청 사이로 비켜 가도
    -- 두 번째 행이 들어오지 못하게 DB에서 못박는다(중복 처리 409의 실제 판정자).
    CONSTRAINT uk_report_decisions_target_type_report_id UNIQUE (target_type, report_id),
    -- 운영자별 처리 이력 조회
    INDEX ix_report_decisions_admin (acted_by_admin_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
