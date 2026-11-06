ALTER TABLE products DROP INDEX ix_products_popular, DROP INDEX ix_products_discount_price, DROP INDEX ix_products_regular_price;
ALTER TABLE products DROP COLUMN review_count;
DELETE FROM perf_applied_ddl WHERE id = 'SEARCH-T06';
