-- 셀러 배송 정책 컬럼. 기본 배송비(base_shipping_fee)와 무료배송 임계(free_shipping_threshold)는
-- 이미 있고 구매자 금액 계산이 읽는 값이라 그대로 두고, 셀러센터 배송 정책 화면이 요구하는 항목만 더한다.
-- sellers는 V27이 CREATE TABLE IF NOT EXISTS로 만든 테이블이라 ddl-auto가 먼저 같은 컬럼을
-- 만들어 둔 DB가 있을 수 있어, V10·V27·V28과 같은 INFORMATION_SCHEMA 조건부 ALTER로 붙인다.

-- 기본 택배사. courier_masters.courier_code 값 참조(FK 없음)
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'default_courier_code'
    ),
    'ALTER TABLE sellers ADD COLUMN default_courier_code VARCHAR(30) NULL',
    'SELECT 1'
);
PREPARE col0_stmt FROM @stmt;
EXECUTE col0_stmt;
DEALLOCATE PREPARE col0_stmt;

-- 반품지가 출고지와 같은지. true면 return_* 3컬럼은 쓰지 않는다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'return_same_as_origin'
    ),
    'ALTER TABLE sellers ADD COLUMN return_same_as_origin BIT(1) NOT NULL DEFAULT b''1''',
    'SELECT 1'
);
PREPARE col1_stmt FROM @stmt;
EXECUTE col1_stmt;
DEALLOCATE PREPARE col1_stmt;

-- 기존 행을 채우려고 DEFAULT를 걸었을 뿐이라 바로 떼어 엔티티 선언과 같은 모양으로 둔다.
-- 기본값이 없는 컬럼에 다시 실행해도 무해하다.
ALTER TABLE sellers
    ALTER COLUMN return_same_as_origin DROP DEFAULT;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'return_zip_code'
    ),
    'ALTER TABLE sellers ADD COLUMN return_zip_code VARCHAR(10) NULL',
    'SELECT 1'
);
PREPARE col2_stmt FROM @stmt;
EXECUTE col2_stmt;
DEALLOCATE PREPARE col2_stmt;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'return_road_address'
    ),
    'ALTER TABLE sellers ADD COLUMN return_road_address VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE col3_stmt FROM @stmt;
EXECUTE col3_stmt;
DEALLOCATE PREPARE col3_stmt;

SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'return_detail_address'
    ),
    'ALTER TABLE sellers ADD COLUMN return_detail_address VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE col4_stmt FROM @stmt;
EXECUTE col4_stmt;
DEALLOCATE PREPARE col4_stmt;

-- 제주 추가 배송비
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'jeju_extra_fee'
    ),
    'ALTER TABLE sellers ADD COLUMN jeju_extra_fee BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE col5_stmt FROM @stmt;
EXECUTE col5_stmt;
DEALLOCATE PREPARE col5_stmt;

ALTER TABLE sellers
    ALTER COLUMN jeju_extra_fee DROP DEFAULT;

-- 도서산간 추가 배송비
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'remote_area_extra_fee'
    ),
    'ALTER TABLE sellers ADD COLUMN remote_area_extra_fee BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE col6_stmt FROM @stmt;
EXECUTE col6_stmt;
DEALLOCATE PREPARE col6_stmt;

ALTER TABLE sellers
    ALTER COLUMN remote_area_extra_fee DROP DEFAULT;

-- 반품 배송비
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'return_fee'
    ),
    'ALTER TABLE sellers ADD COLUMN return_fee BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE col7_stmt FROM @stmt;
EXECUTE col7_stmt;
DEALLOCATE PREPARE col7_stmt;

ALTER TABLE sellers
    ALTER COLUMN return_fee DROP DEFAULT;

-- 교환 배송비(왕복)
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'exchange_fee'
    ),
    'ALTER TABLE sellers ADD COLUMN exchange_fee BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE col8_stmt FROM @stmt;
EXECUTE col8_stmt;
DEALLOCATE PREPARE col8_stmt;

ALTER TABLE sellers
    ALTER COLUMN exchange_fee DROP DEFAULT;

-- 평균 발송 소요일(영업일). 허용 범위 1..7은 도메인이 검증한다
SET @stmt = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'avg_dispatch_days'
    ),
    'ALTER TABLE sellers ADD COLUMN avg_dispatch_days INT NOT NULL DEFAULT 2',
    'SELECT 1'
);
PREPARE col9_stmt FROM @stmt;
EXECUTE col9_stmt;
DEALLOCATE PREPARE col9_stmt;

ALTER TABLE sellers
    ALTER COLUMN avg_dispatch_days DROP DEFAULT;
