-- 운영자 클레임 개입 기록. 누가·언제·무엇을 어떤 근거로 뒤집었는지를 남긴다.
-- 클레임 상태(claims.status = ADMIN_OVERRIDDEN)만으로는 "운영자가 종결했다"까지만 알 수 있어
-- 분쟁에서 근거로 쓸 수 없다.
--
-- claim_id/admin_id는 claims.id·users.id 값 참조라 FK 없음(레포 규약).
-- claim_id UNIQUE: 개입은 클레임을 종결 상태로 보내고 종결 상태에서는 다시 개입할 수 없으므로
-- 한 클레임에 행 하나다. 응용 계층의 상태 판정이 비켜 가도 두 번째 행이 들어오지 못하게 DB에서 못박는다.
--
-- previous_resolved_at이 따로 있는 이유: 개입이 claims.resolved_at을 자기 시각으로 덮는다.
-- 종결 시각 칼럼이 claims에 한 칸뿐이라 이 칼럼이 없으면 셀러가 언제 거부했는지가 사라진다.
--
-- 이 표에는 환불액·결제 축이 없다. 개입은 반품비 부담 주체만 정하고 환불 집행·결제 취소는 건드리지 않는다.

CREATE TABLE IF NOT EXISTS claim_interventions
(
    id                              BIGINT       NOT NULL AUTO_INCREMENT,
    claim_id                        BIGINT       NOT NULL COMMENT 'claims.id 값 참조',
    admin_id                        BIGINT       NOT NULL COMMENT '개입한 운영자 users.id 값 참조',
    fault_decision                  VARCHAR(30)  NOT NULL COMMENT '귀책 판정 SELLER_FAULT/BUYER_FAULT/CLAIM_DISMISSED',
    reason                          VARCHAR(40)  NOT NULL COMMENT '개입 사유 고정 어휘',
    reason_detail                   VARCHAR(500) NULL COMMENT '양쪽에 전달하는 판정 사유 본문',
    return_cost_bearer              VARCHAR(30)  NULL COMMENT '개입이 정한 반품비 부담 주체. 취소·클레임 불가에서는 NULL',
    previous_status                 VARCHAR(30)  NOT NULL COMMENT '뒤집기 직전 클레임 상태 — 무엇을 덮었는가',
    previous_seller_approval_status VARCHAR(30)  NULL COMMENT '뒤집기 직전 셀러 판단 — 거부를 뒤집었는지 미응답을 끝냈는지',
    previous_resolved_at            DATETIME(6)  NULL COMMENT '뒤집기 직전 종결 시각. claims.resolved_at이 덮이므로 여기 남긴다',
    internal_memo                   VARCHAR(300) NULL COMMENT '운영 내부 메모. 셀러·구매자에게 내려보내지 않는다',
    created_at                      DATETIME(6)  NOT NULL COMMENT '개입 시각',
    updated_at                      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_claim_interventions_claim_id UNIQUE (claim_id),
    -- 운영자별 개입 이력 조회
    INDEX ix_claim_interventions_admin (admin_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
