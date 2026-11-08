package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [통합] V22가 버전 행이 없는 기존 상품에 version_no=1을 소급 생성하는지 본다.
 *
 * 주문 품목이 `product_versions`를 NOT NULL로 참조하는데 버전 생성은 상품 등록·수정 시점에만
 * 일어난다. 그 코드가 배포되기 전에 등록된 상품은 버전 행이 0개라 **주문이 불가능하다.** V22가
 * 그 상품들을 메운다.
 *
 * 조건이 `products`·`product_versions` 존재 여부인데 둘 다 Flyway가 만들지 않는다(ddl-auto 소유).
 * 그래서 **CI에서는 이 INSERT가 건너뛰어진다.** 틀려도 터지지 않고 틀린 스냅샷이 남는 종류라
 * 조용히 지나간다.
 *
 * 특히 `thumbnail_key_snapshot`은 NOT NULL인데 그것이 오는 `products.representative_image_key`는
 * NULL을 허용한다. V22가 `COALESCE(..., '')`로 메우는 것이 그 때문인데, 대표 이미지가 없는 상품이
 * 섞이면 그 경로가 유일한 방어다.
 */
class V22BackfillProductVersionsMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `두 테이블이 없는 빈 DB에서는 백필을 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v22_absent")

        migrateTo(url, "22")

        assertThat(appliedSuccessfully(url, "22"))
            .describedAs("V22가 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `버전이 없는 상품에 현재 값으로 version_no 1이 생긴다`() {
        val url = seededDatabase("v22_backfill")

        migrateTo(url, "22")

        assertThat(versions(url))
            .describedAs("버전이 없던 상품 둘만 소급 생성되고 스냅샷이 products의 현재 값과 같아야 한다")
            .containsExactly(
                "1:1:$HARNESS:12000:ON_SALE:products/1/main.webp:SYSTEM",
                "2:1:$BRUSH:8000:SUSPENDED::SYSTEM",
            )
    }

    @Test
    fun `대표 이미지가 없는 상품도 빈 문자열로 들어가 NOT NULL을 어기지 않는다`() {
        val url = seededDatabase("v22_no_image")

        migrateTo(url, "22")

        assertThat(appliedSuccessfully(url, "22"))
            .describedAs("representative_image_key가 NULL인 상품이 있어도 적용이 끝나야 한다")
            .containsExactly("1")
        assertThat(scalar(url, "SELECT thumbnail_key_snapshot FROM product_versions WHERE product_id = 2"))
            .describedAs("NULL이 아니라 빈 문자열이어야 NOT NULL 컬럼에 들어간다")
            .isEqualTo("")
    }

    @Test
    fun `이미 버전이 있는 상품은 건드리지 않는다`() {
        val url = seededDatabase("v22_keep")

        migrateTo(url, "22")

        assertThat(
            query(
                url,
                "SELECT CONCAT(version_no, ':', name_snapshot, ':', changed_by) FROM product_versions WHERE product_id = 3 ORDER BY version_no",
            ),
        ).describedAs("기존 버전 행을 덮거나 version_no 1을 또 만들어 UNIQUE를 어기면 적용이 멈춘다")
            .containsExactly("1:등록 당시 이름:SELLER", "2:$BED:SELLER")
    }

    @Test
    fun `다시 실행해도 행이 늘지 않는다`() {
        val url = seededDatabase("v22_idempotent")
        migrateTo(url, "22")
        val afterFirst = versions(url)

        // 운영에서 수동으로 다시 돌리거나 repair 뒤 재적용되는 경우를 본다.
        execute(url, "DELETE FROM flyway_schema_history WHERE version = '22'")
        migrateTo(url, "22")

        assertThat(versions(url))
            .describedAs("WHERE NOT EXISTS가 막으므로 두 번 돌아도 같아야 한다")
            .isEqualTo(afterFirst)
    }

    /** 버전이 없는 상품 둘(하나는 대표 이미지 없음)과 이미 버전이 둘 있는 상품 하나를 심는다. */
    private fun seededDatabase(name: String): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "22")
        execute(
            url,
            "CREATE TABLE products (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(255) NOT NULL, regular_price BIGINT NOT NULL, " +
                "sale_status VARCHAR(30) NOT NULL, representative_image_key VARCHAR(1024) NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE product_versions (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, version_no INT NOT NULL, " +
                "name_snapshot VARCHAR(255) NOT NULL, price_snapshot BIGINT NOT NULL, " +
                "status_snapshot VARCHAR(30) NOT NULL, thumbnail_key_snapshot VARCHAR(1024) NOT NULL, " +
                "changed_by VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, " +
                "PRIMARY KEY (id), CONSTRAINT uk_product_version_no UNIQUE (product_id, version_no)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO products (id, name, regular_price, sale_status, representative_image_key) VALUES " +
                "(1, '$HARNESS', 12000, 'ON_SALE', 'products/1/main.webp'), " +
                "(2, '$BRUSH', 8000, 'SUSPENDED', NULL), " +
                "(3, '$BED', 45000, 'ON_SALE', 'products/3/main.webp')",
            "INSERT INTO product_versions (product_id, version_no, name_snapshot, price_snapshot, status_snapshot, " +
                "thumbnail_key_snapshot, changed_by, created_at, updated_at) VALUES " +
                "(3, 1, '등록 당시 이름', 50000, 'ON_SALE', 'products/3/old.webp', 'SELLER', NOW(6), NOW(6)), " +
                "(3, 2, '$BED', 45000, 'ON_SALE', 'products/3/main.webp', 'SELLER', NOW(6), NOW(6))",
        )
        return url
    }

    private fun versions(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', product_id, version_no, name_snapshot, price_snapshot, status_snapshot, " +
                "thumbnail_key_snapshot, changed_by) FROM product_versions WHERE changed_by = 'SYSTEM' " +
                "ORDER BY product_id, version_no",
        )

    companion object {
        private const val HARNESS = "리트리버 하네스"
        private const val BRUSH = "장모종용 브러시"
        private const val BED = "대형견 쿠션 침대"
    }
}
