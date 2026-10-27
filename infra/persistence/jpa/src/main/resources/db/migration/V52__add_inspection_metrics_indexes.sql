-- 운영 지표 집계가 쓰는 조회 경로. 컬럼도 테이블도 더하지 않고 인덱스 둘만 더한다 --
-- 지표 넷(단계별 통과율·승급률·백로그 적체·비용 추이)은 V39·V40이 이미 적는 값에서 전부 계산된다.
-- 번호 주의: 머지 직전에 develop 최고 번호를 다시 확인하고 그 뒤로 받는다. out-of-order가
-- 꺼져 있어 낮으면 실행 모듈이 Flyway 검증에서 부팅하지 못한다. 이 스택은 세 번 추월당했다.
-- V39·V40은 앞선 PR 소유다 -- 머지 순서를 맞추느라 번호만 옮겼고 본문은 그대로다.
--
-- 두 인덱스를 엔티티에도 같은 이름으로 선언한다. 한쪽만 두면 ddl-auto로 스키마를 만드는
-- 테스트·로컬 환경만 인덱스 없이 돌아 조회 계획이 환경 간에 갈린다(V41의 큐 인덱스와 같은 규약).
--
-- 조건부로 만든다. dev는 Flyway와 ddl-auto(update)를 함께 쓴다. 이 파일이 한 번 실패해
-- 미적용으로 남으면 Hibernate가 엔티티 선언을 보고 같은 이름의 인덱스를 먼저 만들고,
-- MySQL에는 CREATE INDEX IF NOT EXISTS가 없어 그 뒤로 이 파일이 영구히 적용 불가가 된다.

-- 비율 전부의 앵커가 content_inspections.settled_at이라 원장은 검수 id로 붙는다.
-- 기존 인덱스 둘(uk_ai_call_ledger_call의 call_id, ix_ai_call_ledger_created의 (created_at, model_id))에
-- inspection_id가 없어 붙일 때마다 전체 스캔이다. 검수 1건이 호출 1~5건을 낳는 테이블이다.
-- 관점을 둘째 칼럼으로 둔다 -- 승급률의 분모(perspective = 'TRIAGE')와 단계별 비용이 그 축으로 묶는다.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ai_call_ledger'
                     AND INDEX_NAME = 'ix_ai_call_ledger_inspection');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_ai_call_ledger_inspection ON ai_call_ledger (inspection_id, perspective)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 모든 비율의 분모가 "창 안에 닫힌 검수"라 settled_at으로 좁힌다. 기존 인덱스 둘
-- ((processing_state, next_retry_at), (target_type, target_ref, id))에는 그 칼럼이 없다.
-- 이 테이블은 (대상, 내용 버전, 정책)마다 한 행씩 쌓여 계속 자란다.
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'content_inspections'
                     AND INDEX_NAME = 'ix_content_inspections_settled');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_content_inspections_settled ON content_inspections (settled_at)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
