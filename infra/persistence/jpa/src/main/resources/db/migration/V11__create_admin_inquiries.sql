-- 앱 서비스 문의. v1은 접수 + 운영팀 메일 전달까지.
-- 어드민 처리 컬럼(order_id, claim_id, title, assigned_admin_id, answer, resolved_at)은 nullable, v1 미사용.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.
-- V11 채번 — 현재 최신이 V10이라 max+1.
CREATE TABLE IF NOT EXISTS admin_inquiries
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,                 -- 작성 구매자(값 참조, BC 경계)
    inquiry_type      VARCHAR(30)  NOT NULL,                 -- ACCOUNT / ORDER_PAYMENT / DELIVERY_CLAIM / SELLER_PRODUCT / SERVICE_ETC
    order_id          BIGINT       NULL,                     -- 분쟁 대상 주문(v1 미사용)
    claim_id          BIGINT       NULL,                     -- 연관 클레임(v1 미사용)
    title             VARCHAR(200) NULL,                     -- 제목(v1 미사용)
    content           TEXT         NOT NULL,                 -- 문의 내용
    status            VARCHAR(20)  NOT NULL,                 -- RECEIVED / IN_PROGRESS / DONE / ON_HOLD (v1=RECEIVED)
    assigned_admin_id BIGINT       NULL,                     -- 배정 관리자(v1 미사용)
    answer            TEXT         NULL,                      -- 어드민 답변(v1 미사용)
    reply_email       VARCHAR(255) NOT NULL,                 -- 답변 수신 이메일(알림 메일 Reply-To)
    resolved_at       DATETIME(6)  NULL,                     -- 처리 완료 시각(v1 미사용)
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_inquiries_user_created_at (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
