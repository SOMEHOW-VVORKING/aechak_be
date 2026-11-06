-- 프로필 영상 탭이 sns_posts를 읽는 경로. 컬럼은 그대로 두고 인덱스 하나만 추가한다.
-- 엔티티 Post에도 같은 이름과 열로 선언한다. InnoDB 보조 인덱스 끝에 PK가 붙어 (created_at, id) keyset이 인덱스 순서와 같다.
--
-- 조건부로 만든다. dev는 Flyway와 ddl-auto(update)를 함께 써서 이 파일이 한 번 실패하면
-- Hibernate가 엔티티 선언을 보고 같은 이름의 인덱스를 먼저 만들고, MySQL에는 CREATE INDEX IF NOT EXISTS가 없다.

-- 주인의 영상 원글 최신순
SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sns_posts'
                     AND INDEX_NAME = 'ix_sns_posts_author_media');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_sns_posts_author_media ON sns_posts (author_id, media_type, status, created_at)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
