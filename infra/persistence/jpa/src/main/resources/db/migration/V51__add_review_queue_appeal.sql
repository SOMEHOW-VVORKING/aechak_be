-- 셀러 이의 제기. 검토 큐 행에 이의 축을 붙임.
-- 번호 주의: 머지 직전에 develop 최고 번호를 다시 확인하고 그 뒤로 받는다. out-of-order가
-- 꺼져 있어 낮으면 실행 모듈이 Flyway 검증에서 부팅하지 못한다. 이 스택은 세 번 추월당했다 --
-- 열린 PR이 재채번되면 그 번호가 움직이므로 미리 따 두는 것이 통하지 않는다.
-- 이의 상태를 따로 열거로 두지 않음 — appealed_at(걸렸는가)과 status(행이 어떻게 닫혔는가)에서 파생됨.
-- 접수는 노출을 바꾸지 않으므로 block_source는 그대로 AI_DETECTION임(원래 가린 것이 AI다).
-- appeal_attempt_count는 무효화가 지우지 않는 유일한 이의 축이다. 무효화는 재접수를 열어 주려고
-- appealed_at을 비우는데, 그 칼럼이 상한의 분모까지 겸하면 "한 글자 고쳐 새 판정을 받고 다시 접수"가
-- 아무 상한에도 걸리지 않고 무한히 돈다. 무효가 된 이의의 흔적도 이 숫자가 든다.
ALTER TABLE product_review_queue_items
    ADD COLUMN appeal_reason        VARCHAR(500) NULL COMMENT '셀러가 쓴 문장. 관리자에게 그대로 보임',
    ADD COLUMN appealed_at          DATETIME(6)  NULL COMMENT 'NULL이면 이의 없음. 관리자 이의 필터의 축',
    ADD COLUMN appealed_content_hash VARCHAR(64) NULL COMMENT '이의를 접수한 시점의 내용 지문. 이의 무효 판단의 축',
    ADD COLUMN appeal_attempt_count INT          NOT NULL DEFAULT 0 COMMENT '이 행에 접수된 횟수. 무효화가 지우지 않음 — 되돌이 상한과 무효 흔적의 축',
    ADD COLUMN resolved_at          DATETIME(6)  NULL COMMENT '행이 닫힌 시각. 기각 쿨다운의 기준';

-- 닫힌 시각을 과거 행에 연결한다. 반려 이력의 표시 시각이 updated_at에서 이 칼럼으로 옮겨
-- 갔으므로, 백필하지 않으면 이 파일 전에 닫힌 행의 반려 시각이 화면에서 전부 NULL이 된다.
-- 머지·배포 순서에 기대지 않는다 — 큐 테이블을 만드는 파일과 이 파일이 서로 다른 머지 단위라
-- 그 사이에 관리자가 반려한 행이 생길 수 있다. 닫힌 행에는 더 쓰지 않으므로 updated_at이
-- 그 행이 닫힌 시각이다.
UPDATE product_review_queue_items
SET resolved_at = updated_at
WHERE status <> 'PENDING_REVIEW'
  AND resolved_at IS NULL;

-- 셀러별 열린 이의 건수(폭주 상한의 분모)를 세는 경로.
-- 조건부로 만듦: 큐 테이블을 만드는 V40과 달리 기존 테이블에 붙어서, 이 파일이 한 번 실패해 미적용으로 남으면
-- ddl-auto(update)가 엔티티 선언을 보고 같은 이름 인덱스를 먼저 만들고, MySQL에
-- CREATE INDEX IF NOT EXISTS가 없어 그 뒤로 영구 적용 불가가 됨.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'product_review_queue_items'
                     AND INDEX_NAME = 'ix_product_review_queue_items_owner');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_product_review_queue_items_owner ON product_review_queue_items (owner_id, status)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
