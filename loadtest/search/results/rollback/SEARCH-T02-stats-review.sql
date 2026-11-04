ALTER TABLE product_stats DROP INDEX ix_product_stats_review;
DELETE FROM perf_applied_ddl WHERE id = 'SEARCH-T02-stats-review';
