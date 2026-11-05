-- 검색이 sellers에서 읽는 상태와 상점 이름을 담은 커버링 인덱스
ALTER TABLE sellers ADD INDEX ix_sellers_active (user_id, status, store_name);
