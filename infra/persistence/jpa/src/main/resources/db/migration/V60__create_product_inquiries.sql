-- product_inquiries, product_inquiry_answers 스키마 — 구매자가 상품에 묻고 셀러가 답하는 상품 문의.
-- 기존 admin_inquiries는 앱 서비스 문의(계정·결제·배송)이고 이 테이블과 무관하다.
-- prod는 ddl-auto=none이라 스키마를 여기서 보장하고, 엔티티 자동 생성과 겹칠 수 있어 IF NOT EXISTS로 가드한다.

-- product_id·seller_id·author_user_id는 BC 경계 밖이라 FK를 걸지 않는다.
-- product_public_id·product_name_snapshot은 접수 시점 복사본이다 — 상품이 판매 중지·삭제돼도
-- 문의와 셀러의 받은 목록이 남아야 하고, 셀러 소유권 판정도 이 복사본으로 한다.
-- answer_due_at은 접수 시점에 서버가 계산한 답변 기한(영업일 2일)이다. 화면이 날짜를 빼서
-- 다시 계산하면 지표와 행의 "기한 지남"이 어긋나므로 저장해 두고 집계도 이 컬럼으로 센다.
CREATE TABLE IF NOT EXISTS product_inquiries
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    public_id             VARCHAR(26)  NOT NULL,              -- ULID. 외부에 내보내는 식별자
    product_id            BIGINT       NOT NULL,
    product_public_id     VARCHAR(26)  NOT NULL,              -- 접수 시점 상품 식별자 복사본
    seller_id             BIGINT       NOT NULL,              -- 접수 시점 상품 소유 셀러(users.id)
    author_user_id        BIGINT       NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    category              VARCHAR(30)  NOT NULL,              -- PRODUCT/DELIVERY/CLAIM/ETC
    title                 VARCHAR(100) NOT NULL,
    content               TEXT         NOT NULL,
    secret                BIT(1)       NOT NULL,              -- 비밀글. 작성자와 해당 셀러만 본문을 읽는다
    answer_due_at         DATETIME(6)  NOT NULL,
    status                VARCHAR(30)  NOT NULL,              -- RECEIVED/ANSWERED/WITHDRAWN
    answered_at           DATETIME(6)  NULL,
    withdrawn_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_inquiries_public_id UNIQUE (public_id),
    INDEX ix_product_inquiries_product_status_id (product_id, status, id),
    INDEX ix_product_inquiries_seller_status_id (seller_id, status, id),
    INDEX ix_product_inquiries_author_id (author_user_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 답변은 문의당 최대 한 건 — 상태 가드가 두 번째 답변을 막고 여기의 UNIQUE가 함께 받친다.
CREATE TABLE IF NOT EXISTS product_inquiry_answers
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    inquiry_id       BIGINT      NOT NULL,
    answerer_user_id BIGINT      NOT NULL,
    content          TEXT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_inquiry_answers_inquiry_id UNIQUE (inquiry_id),
    CONSTRAINT fk_product_inquiry_answers_inquiry FOREIGN KEY (inquiry_id) REFERENCES product_inquiries (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
