ALTER TABLE sellers DROP INDEX ix_sellers_active;
DELETE FROM perf_applied_ddl WHERE id = 'SEARCH-T02-sellers-active';
