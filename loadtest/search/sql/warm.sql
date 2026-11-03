-- 버퍼 풀을 채우는 고정 warm set. reseed, 재부팅, 스냅샷 복원 뒤에 돌린다.
-- 검색이 읽는 네 테이블의 B-tree 인덱스(PRIMARY 포함)를 하나씩 FORCE INDEX로 끝까지 읽는다.
-- 인덱스 목록을 실행 시점에 읽어서 ddl로 더한 인덱스도 함께 읽는다. FULLTEXT는 COUNT로 읽히지 않아 뺀다.
-- 결과는 index<TAB>rows 행이다.

SET SESSION group_concat_max_len = 1048576;

SELECT GROUP_CONCAT(
               CONCAT('SELECT ', QUOTE(CONCAT(table_name, '.', index_name)), ', COUNT(*) FROM `', table_name,
                      '` FORCE INDEX (`', index_name, '`)')
               ORDER BY table_name, index_name SEPARATOR ' UNION ALL ')
INTO @warm
FROM (SELECT DISTINCT table_name, index_name
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name IN ('products', 'product_stats', 'sellers', 'categories')
        AND index_type = 'BTREE') s;

PREPARE stmt FROM @warm;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
