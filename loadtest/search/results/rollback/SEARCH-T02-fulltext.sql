-- FTS_DOC_ID 열과 FTS 보조 테이블은 인덱스를 지워도 남는다
ALTER TABLE products DROP INDEX ft_products_name;
DELETE FROM perf_applied_ddl WHERE id = 'SEARCH-T02-fulltext';
