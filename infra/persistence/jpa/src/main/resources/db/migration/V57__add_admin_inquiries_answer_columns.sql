-- 어드민 문의 답변 기록과 운영 목록 조회 인덱스.
-- answer / assigned_admin_id / resolved_at은 V14가 nullable로 깔아 둔 컬럼을 그대로 쓴다 — 여기서는 답변자 식별자만 더한다.
ALTER TABLE admin_inquiries
    ADD COLUMN answered_by_admin_id BIGINT NULL AFTER answer, -- 답변을 등록한 운영자(책임 추적용, 등록 후 불변)
    -- 운영 목록은 상태 필터 + 접수 오래된 순(created_at, id) 커서로 읽는다. id를 꼬리에 둬 커버링 + 동시각 타이브레이크.
    ADD INDEX idx_admin_inquiries_status_created_at (status, created_at, id),
    -- 상태 필터가 없는 전체 목록용 — PK는 created_at 정렬에 쓸 수 없다.
    ADD INDEX idx_admin_inquiries_created_at_id (created_at, id);
