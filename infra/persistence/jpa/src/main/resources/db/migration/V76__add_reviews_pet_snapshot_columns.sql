-- 리뷰 작성 시점의 작성자 대표 펫(SCRUM-326). 리뷰 AI 요약의 체급 묶음과 반려동물 기반 리뷰 선별이 쓴다.
-- 번호 주의: 열린 PR이 V55~V61을 쓰고 있어 V62로 잡았다. 머지 직전에 develop 최고 번호를 다시 확인한다.
--
-- 조건부로 만든다. dev는 Flyway와 ddl-auto(update)를 함께 써서, 이 파일이 한 번 실패해 미적용으로 남으면
-- Hibernate가 엔티티를 보고 같은 컬럼을 먼저 만들고 그 뒤로 이 파일이 영구히 적용되지 않는다.

-- 기존 리뷰는 작성 당시 값을 알 수 없어 비워 둔다
SET @col_exists = (SELECT COUNT(*)
                   FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'reviews'
                     AND COLUMN_NAME = 'pet_species');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE reviews
                   ADD COLUMN pet_species          VARCHAR(10)  NULL COMMENT ''작성 시점 대표 펫의 종'',
                   ADD COLUMN pet_breed_id         BIGINT       NULL COMMENT ''작성 시점 대표 펫의 품종'',
                   ADD COLUMN pet_birth_year_month VARCHAR(7)   NULL COMMENT ''작성 시점 대표 펫의 생년월'',
                   ADD COLUMN pet_weight           DECIMAL(4, 1) NULL COMMENT ''작성 시점 대표 펫의 체중(kg)'',
                   ADD COLUMN pet_segment_key      VARCHAR(30)  NULL COMMENT ''요약 체급 묶음 키. 체중을 모르면 NULL''',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
