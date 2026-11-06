-- 탈퇴 정리가 사용자의 좋아요 행을 찾는 경로. 컬럼은 그대로 두고 두 좋아요 표에 user_id 인덱스만 추가한다.
-- 엔티티 PostLike, CommentLike에도 같은 이름과 열로 선언한다.
--
-- 조건부로 만든다(V94와 같은 이유). dev는 Flyway와 ddl-auto(update)를 함께 써서 이 파일이 한 번 실패하면
-- Hibernate가 엔티티 선언을 보고 같은 이름의 인덱스를 먼저 만들고, MySQL에는 CREATE INDEX IF NOT EXISTS가 없다.

SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sns_post_likes'
                     AND INDEX_NAME = 'ix_sns_post_likes_user');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_sns_post_likes_user ON sns_post_likes (user_id)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sns_comment_likes'
                     AND INDEX_NAME = 'ix_sns_comment_likes_user');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX ix_sns_comment_likes_user ON sns_comment_likes (user_id)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
