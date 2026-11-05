-- 노출 조건(검수 승인, 판매 상태) 인덱스. 상품 대부분이 조건을 통과해 선택도가 낮다
ALTER TABLE products ADD INDEX ix_products_visible (inspection_status, sale_status, id);
