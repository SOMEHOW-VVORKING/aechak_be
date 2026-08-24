-- 주문품목이 product_versions를 NOT NULL로 참조하는데, 버전 생성은 상품 등록·수정 시점에만 일어난다.
-- 그 코드가 배포되기 전에 등록된 상품은 버전 행이 0개라 주문이 불가능하므로, 현재 products 값으로
-- version_no=1을 소급 생성한다. changed_by=SYSTEM이 소급 생성 표식.
-- 스냅샷 매핑은 등록 경로의 ProductVersion.create와 동일: name / regular_price / sale_status / representative_image_key.
-- 대상 테이블은 Flyway 관리 밖(ddl-auto 생성)이라 테이블이 없는 새 DB에서는 건너뛴다(V15 선례).
-- V15(product_versions 제약 정리)가 먼저 적용된 뒤 돌아야 한다.

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    ) AND EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_versions'
    ),
    'INSERT INTO product_versions
        (product_id, version_no, name_snapshot, price_snapshot, status_snapshot,
         thumbnail_key_snapshot, changed_by, created_at, updated_at)
     SELECT p.id, 1, p.name, p.regular_price, p.sale_status,
            COALESCE(p.representative_image_key, ''''),
            ''SYSTEM'', NOW(6), NOW(6)
     FROM products p
     WHERE NOT EXISTS (SELECT 1 FROM product_versions v WHERE v.product_id = p.id)',
    'SELECT 1'
);
PREPARE backfill_product_versions_stmt FROM @stmt;
EXECUTE backfill_product_versions_stmt;
DEALLOCATE PREPARE backfill_product_versions_stmt;
