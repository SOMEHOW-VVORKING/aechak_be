-- 셀러 이의 제기. 검토 큐 행에 이의 축을 붙임.
-- 번호 주의: develop이 V34, 열린 PR이 V35~V40을 점유해 그 다음 번호를 받음.
-- 이의 상태를 따로 열거로 두지 않음 — appealed_at(걸렸는가)과 status(행이 어떻게 닫혔는가)에서 파생됨.
-- 접수는 노출을 바꾸지 않으므로 block_source는 그대로 AI_DETECTION임(원래 가린 것이 AI다).
ALTER TABLE product_review_queue_items
    ADD COLUMN appeal_reason VARCHAR(500) NULL COMMENT '셀러가 쓴 문장. 관리자에게 그대로 보임',
    ADD COLUMN appealed_at   DATETIME(6) NULL COMMENT 'NULL이면 이의 없음. 관리자 이의 필터의 축',
    ADD COLUMN resolved_at   DATETIME(6) NULL COMMENT '행이 닫힌 시각. 기각 쿨다운의 기준';

-- 셀러별 열린 이의 건수(폭주 상한의 분모)를 세는 경로.
-- 조건부로 만듦: V39와 달리 기존 테이블에 붙어서, 이 파일이 한 번 실패해 미적용으로 남으면
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
