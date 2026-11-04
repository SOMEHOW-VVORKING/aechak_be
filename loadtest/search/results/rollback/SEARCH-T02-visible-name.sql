ALTER TABLE products DROP INDEX ix_products_visible_name;
DELETE FROM perf_applied_ddl WHERE id = 'SEARCH-T02-visible-name';
