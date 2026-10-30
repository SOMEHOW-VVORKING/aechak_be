-- 어드민 주문 배송 모니터링: 발송 독촉 기록 테이블과 전체 주문 조회 인덱스 둘 --
--
-- 번호 주의: 머지 직전에 develop 최고 번호를 다시 확인하고 그 뒤로 받는다. out-of-order가
-- 꺼져 있어 낮으면 실행 모듈이 Flyway 검증에서 부팅하지 못한다.
--
-- 인덱스는 조건부로 만든다. dev는 Flyway와 ddl-auto(update)를 함께 쓴다. 이 파일이 한 번 실패해
-- 미적용으로 남으면 Hibernate가 엔티티 선언을 보고 같은 이름의 인덱스를 먼저 만들고,
-- MySQL에는 CREATE INDEX IF NOT EXISTS가 없어 그 뒤로 이 파일이 영구히 적용 불가가 된다.
-- 두 인덱스는 엔티티(Order)에도 같은 이름으로 선언한다 — 한쪽만 두면 ddl-auto로 스키마를 만드는
-- 테스트·로컬만 인덱스 없이 돌아 조회 계획이 환경 간에 갈린다(V52와 같은 규약).

-- 운영자가 셀러에게 발송을 독촉한 기록. append-only.
-- 알림 발송은 하지 않는다 — 어드민 실행 모듈이 발송 어댑터(:ses-client)를 의존하지 않아 보낼 수단이 없다.
-- BC 밖 참조(admin_user_id)와 같은 BC 부모 참조(order_id) 모두 팀 컨벤션대로 FK 없이 인덱스만 둔다.
-- created_at/updated_at은 BaseEntity가 채우므로 DB 기본값 없음.
CREATE TABLE IF NOT EXISTS order_dispatch_nudges
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,              -- 값 참조(orders)
    admin_user_id BIGINT       NOT NULL,              -- 값 참조(user BC). 누가 눌렀는지가 운영 이력의 핵심
    memo          VARCHAR(300) NULL,                  -- 셀러에게 전달할 사유. 공백만이면 NULL로 접어 저장한다
    created_at    DATETIME(6)  NOT NULL,              -- 독촉 시각. 쿨다운 판정의 기준
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 쿨다운은 "이 주문의 최신 한 건"을 본다 — id 내림차순 첫 행이라 (order_id, id)로 끝난다
    INDEX idx_order_dispatch_nudges_order_id (order_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 어드민 목록의 기본 정렬·커서·기간 필터가 모두 이 축이다. 셀러 스코프가 없어 모수가 전체 주문이고,
-- orders에는 created_at 인덱스가 없어 매 페이지가 테이블 전체를 정렬한다.
-- id까지 넣는 이유는 커서가 (주문 시각, 주문 id) 두 칼럼 비교라서다 — 같은 시각 행이 갈리지 않으면
-- 한 건이 두 페이지에 나오거나 건너뛰어진다.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND INDEX_NAME = 'idx_orders_created_at_id');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_orders_created_at_id ON orders (created_at, id)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 상태 한 종을 골라 주문 시각 내림차순으로 보는 경로(배송중만, 상품준비만). 기존
-- idx_orders_seller_id_status는 선두가 seller_id라 셀러를 고르지 않은 조회에는 쓰이지 않는다.
-- 셀러 필터는 그 기존 인덱스가 받고 정렬만 추가로 든다 — 한 셀러의 주문 수는 전체에 비해 작아
-- 인덱스를 하나 더 늘리는 쓰기 비용보다 싸다고 보고 seller_id + created_at 인덱스는 두지 않았다.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND INDEX_NAME = 'idx_orders_status_created_at');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_orders_status_created_at ON orders (status, created_at, id)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
