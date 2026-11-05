-- 지표 수집용 exporter 계정을 만들거나 비밀번호를 다시 맞춘다. 마스터 계정으로 실행한다.
-- 비밀번호는 호출하는 쪽이 같은 세션에서 먼저 SET 하고 이 파일을 stdin으로 이어 보낸다.
-- 명령줄 인자에 남지 않게 stdin으로만 넘긴다. 값에 작은따옴표나 역슬래시가 없어야 한다.
--   (printf "SET @exporter_password := '%s';\n" "$PW"; cat sql/init-users.sql) | mysql ... aechak
-- @exporter_password가 비어 있으면 PREPARE가 NULL을 받아 오류로 멈춘다.

SET @pw := IF(COALESCE(@exporter_password, '') = '', NULL, QUOTE(@exporter_password));

SET @sql := CONCAT('CREATE USER IF NOT EXISTS ''exporter''@''%'' IDENTIFIED BY ', @pw);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := CONCAT('ALTER USER ''exporter''@''%'' IDENTIFIED BY ', @pw, ' WITH MAX_USER_CONNECTIONS 3');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';

SET @pw := NULL;
SET @sql := NULL;
SET @exporter_password := NULL;
