package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [통합] V7이 반려동물 프로필의 옛 컬럼을 정리하는 경로를 본다.
 *
 * `pet_profiles` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유). 그래서 **CI 에서는 두 단계가
 * 건너뛰어진다.**
 *
 * 깨지면 NOT NULL 인 옛 컬럼이 남아 새 INSERT 가 기본값 없음(1364)으로 깨진다. 그것이 이
 * 마이그레이션을 만든 이유다.
 *
 * 두 단계가 서로 독립 조건이라 **한쪽만 적용된 DB** 에서도 나머지가 돌아야 한다. ddl-auto 가
 * 엔티티 선언을 따라 한쪽을 미리 처리해 둔 DB 가 그 경우다.
 */
class V7PetProfileColumnCleanupMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `테이블이 없는 빈 DB에서는 두 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v7_absent")

        migrateTo(url, "7")

        assertThat(appliedSuccessfully(url, "7"))
            .describedAs("V7이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `옛 컬럼이 둘 다 있으면 is_primary를 개명하고 species를 지운다`() {
        val url = seededDatabase("v7_both", hasIsPrimary = true, hasSpecies = true)

        migrateTo(url, "7")

        assertThat(columns(url))
            .describedAs("is_default 로 개명되고 species 는 사라져야 한다. breed_id 가 남아 종은 복원된다")
            .containsExactly("breed_id", "id", "is_default")
        assertThat(query(url, "SELECT CONCAT(id, ':', is_default) FROM pet_profiles ORDER BY id"))
            .describedAs("개명이라 값이 그대로 옮겨져야 한다")
            .containsExactly("1:1", "2:0")
    }

    @Test
    fun `is_primary가 이미 개명된 DB에서는 개명을 건너뛰고 species만 지운다`() {
        val url = seededDatabase("v7_renamed", hasIsPrimary = false, hasSpecies = true)

        migrateTo(url, "7")

        assertThat(columns(url)).containsExactly("breed_id", "id", "is_default")
        assertThat(appliedSuccessfully(url, "7")).containsExactly("1")
    }

    @Test
    fun `species가 이미 없는 DB에서는 개명만 하고 끝난다`() {
        val url = seededDatabase("v7_no_species", hasIsPrimary = true, hasSpecies = false)

        migrateTo(url, "7")

        assertThat(columns(url)).containsExactly("breed_id", "id", "is_default")
        assertThat(appliedSuccessfully(url, "7")).containsExactly("1")
    }

    private fun columns(url: String): List<String> =
        query(
            url,
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS $IN_SCHEMA " +
                "AND TABLE_NAME = 'pet_profiles' ORDER BY COLUMN_NAME",
        )

    private fun seededDatabase(
        name: String,
        hasIsPrimary: Boolean,
        hasSpecies: Boolean,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "7")
        val defaultColumn = if (hasIsPrimary) "is_primary" else "is_default"
        val speciesColumn = if (hasSpecies) ", species VARCHAR(30) NOT NULL" else ""
        val speciesValue = if (hasSpecies) ", 'DOG'" else ""
        val speciesName = if (hasSpecies) ", species" else ""
        execute(
            url,
            "CREATE TABLE pet_profiles (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, breed_id BIGINT NOT NULL, " +
                "$defaultColumn TINYINT(1) NOT NULL$speciesColumn, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO pet_profiles (id, breed_id, $defaultColumn$speciesName) VALUES " +
                "(1, 1, 1$speciesValue), (2, 1, 0$speciesValue)",
        )
        return url
    }
}
