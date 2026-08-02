-- ddl-auto=update는 컬럼을 추가만 하고 이름 변경·삭제는 안 함.
-- 그대로 두면 NOT NULL인 옛 컬럼이 남아 새 INSERT가 기본값 없음(1364)으로 깨짐.
-- 아직 이 테이블을 쓰는 API가 배포된 적 없어 데이터 이관은 불필요.
-- 테이블이 없는 새 DB에서는 ddl-auto가 처음부터 is_default로 만들므로 건너뜀.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pet_profiles'
          AND COLUMN_NAME = 'is_primary'
    ),
    'ALTER TABLE pet_profiles RENAME COLUMN is_primary TO is_default',
    'SELECT 1'
);
PREPARE rename_stmt FROM @stmt;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;

-- species는 breed_id로 결정되는 값이라 컬럼을 지운다. 두 값이 어긋난 상태를 만들 수 없게 하는 게 목적.
-- breed_id가 남아 있어 복원 가능하므로 데이터 손실은 없다.
SET @drop_species = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pet_profiles'
          AND COLUMN_NAME = 'species'
    ),
    'ALTER TABLE pet_profiles DROP COLUMN species',
    'SELECT 1'
);
PREPARE drop_stmt FROM @drop_species;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;
