-- 탈퇴 정리가 생기기 전에 탈퇴한 사용자의 SNS 데이터를 한 번 정리하고 수를 다시 센다.
-- 탈퇴할 때의 정리와 같은 결과를 만든다. 게시물과 댓글은 사유 WITHDRAWAL로 지우고 팔로우, 리포스트, 저장, 좋아요와
-- 그 사용자가 건 차단과 뮤트, 그 사용자의 통계 행을 지운다. 다른 사용자가 탈퇴한 사용자에게 건 차단과 뮤트는 남긴다.
-- 지운 게시물의 미디어 파일은 SQL로 지울 수 없어 스토리지에 남는다.
-- 다시 실행해도 결과가 같다. users는 ddl-auto가 만드는 테이블이라 테이블이 없는 빈 DB에서는 탈퇴한 사용자가 없는 것으로 본다(V72와 같은 조건).

CREATE TEMPORARY TABLE tmp_sns_withdrawn_users
(
    user_id BIGINT NOT NULL PRIMARY KEY
);

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
    ),
    'INSERT INTO tmp_sns_withdrawn_users (user_id) SELECT id FROM users WHERE status = ''WITHDRAWN''',
    'DO 0'
);
PREPARE collect_withdrawn_users_stmt FROM @stmt;
EXECUTE collect_withdrawn_users_stmt;
DEALLOCATE PREPARE collect_withdrawn_users_stmt;

-- 게시물. 지우면 태그 연결도 지운다(Post.delete와 같다)
UPDATE sns_posts p
    JOIN tmp_sns_withdrawn_users w ON w.user_id = p.author_id
SET p.status          = 'DELETED',
    p.deletion_reason = 'WITHDRAWAL',
    p.deleted_at      = NOW(6),
    p.caption         = NULL,
    p.updated_at      = NOW(6)
WHERE p.status = 'PUBLISHED';

DELETE ph
FROM sns_post_hashtags ph
    JOIN sns_posts p ON p.id = ph.post_id
WHERE p.status = 'DELETED';

-- 댓글. 내용은 남기고 API로 내보내지 않는다(Comment.delete와 같다)
UPDATE sns_comments c
    JOIN tmp_sns_withdrawn_users w ON w.user_id = c.author_id
SET c.status          = 'DELETED',
    c.deletion_reason = 'WITHDRAWAL',
    c.deleted_at      = NOW(6),
    c.updated_at      = NOW(6)
WHERE c.status = 'ACTIVE';

-- 관계와 반응. 임시 테이블은 한 문장에서 한 번만 읽을 수 있어 방향마다 지운다
DELETE f FROM sns_follows f JOIN tmp_sns_withdrawn_users w ON w.user_id = f.follower_id;
DELETE f FROM sns_follows f JOIN tmp_sns_withdrawn_users w ON w.user_id = f.followee_id;
DELETE b FROM sns_blocks b JOIN tmp_sns_withdrawn_users w ON w.user_id = b.blocker_id;
DELETE m FROM sns_mutes m JOIN tmp_sns_withdrawn_users w ON w.user_id = m.muter_id;
DELETE r FROM sns_reposts r JOIN tmp_sns_withdrawn_users w ON w.user_id = r.reposter_id;
DELETE s FROM sns_saves s JOIN tmp_sns_withdrawn_users w ON w.user_id = s.user_id;
DELETE l FROM sns_post_likes l JOIN tmp_sns_withdrawn_users w ON w.user_id = l.user_id;
DELETE l FROM sns_comment_likes l JOIN tmp_sns_withdrawn_users w ON w.user_id = l.user_id;
DELETE s FROM sns_user_stats s JOIN tmp_sns_withdrawn_users w ON w.user_id = s.user_id;

-- 수를 행에서 다시 센다. 값이 다른 행만 바꾼다
UPDATE sns_user_stats s
    LEFT JOIN (SELECT followee_id AS user_id, COUNT(*) AS cnt FROM sns_follows GROUP BY followee_id) fr
    ON fr.user_id = s.user_id
    LEFT JOIN (SELECT follower_id AS user_id, COUNT(*) AS cnt FROM sns_follows GROUP BY follower_id) fg
    ON fg.user_id = s.user_id
    LEFT JOIN (SELECT author_id AS user_id, COUNT(*) AS cnt FROM sns_posts WHERE status = 'PUBLISHED' GROUP BY author_id) pc
    ON pc.user_id = s.user_id
SET s.follower_count  = COALESCE(fr.cnt, 0),
    s.following_count = COALESCE(fg.cnt, 0),
    s.post_count      = COALESCE(pc.cnt, 0),
    s.updated_at      = NOW(6)
WHERE s.follower_count <> COALESCE(fr.cnt, 0)
   OR s.following_count <> COALESCE(fg.cnt, 0)
   OR s.post_count <> COALESCE(pc.cnt, 0);

UPDATE sns_post_stats s
    LEFT JOIN (SELECT post_id, COUNT(*) AS cnt FROM sns_reposts GROUP BY post_id) r ON r.post_id = s.post_id
SET s.repost_count = COALESCE(r.cnt, 0),
    s.updated_at   = NOW(6)
WHERE s.repost_count <> COALESCE(r.cnt, 0);

UPDATE sns_hashtags h
    LEFT JOIN (SELECT ph.hashtag_id, COUNT(*) AS cnt
               FROM sns_post_hashtags ph
                   JOIN sns_posts p ON p.id = ph.post_id
               WHERE p.status = 'PUBLISHED'
               GROUP BY ph.hashtag_id) c ON c.hashtag_id = h.id
SET h.post_count = COALESCE(c.cnt, 0),
    h.updated_at = NOW(6)
WHERE h.post_count <> COALESCE(c.cnt, 0);

DROP TEMPORARY TABLE tmp_sns_withdrawn_users;
