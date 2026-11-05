-- 노출 조건 인덱스에 상품명을 더해 설명 TEXT가 든 클러스터 행을 읽지 않고 상품명 조건을 거를 수 있게 한다
ALTER TABLE products ADD INDEX ix_products_visible_name (inspection_status, sale_status, id, name);
