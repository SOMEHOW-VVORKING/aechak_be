package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestConfig
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * V33(클레임 사유 코드 고정 어휘 이관)이 자유 문자열 시절 데이터를 어떻게 옮기는지 본다.
 * 깨지면 dev에 남은 값이 어휘 밖으로 새고, 그 행을 읽는 순간 enum 변환에서 조회가 터진다.
 *
 * 컬럼 모양 대조는 V27 테스트가 이미 claims를 보고 있어 여기서는 데이터만 본다.
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V33ClaimReasonMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var migratedUrl: String

    @BeforeAll
    fun migrateLegacyRows() {
        migratedUrl = recreateDatabase("v33_legacy")
        migrateUpTo(migratedUrl, "32")
        execute(migratedUrl, legacyClaimRows())
        flyway(migratedUrl).migrate()
    }

    @Test
    fun `어휘와 맞는 값은 그대로 두고 대소문자만 흔들린 값은 글자를 맞춘다`() {
        assertThat(reasonOf(1)).isEqualTo("CHANGE_OF_MIND" to "옵션을 잘못 골랐어요")
        assertThat(reasonOf(2)).describedAs("소문자가 남으면 enum 변환에서 터진다").isEqualTo("CHANGE_OF_MIND" to "소문자로 심긴 사유")
        assertThat(reasonOf(3)).describedAs("앞뒤 공백도 걷어내야 한다").isEqualTo("DELIVERY_DELAY" to null)
    }

    @Test
    fun `뜻이 하나로 읽히는 옛 표기는 새 코드로 옮긴다`() {
        assertThat(reasonOf(4)).isEqualTo("DEFECTIVE" to null)
        assertThat(reasonOf(5)).isEqualTo("CHANGE_OF_MIND" to null)
    }

    @Test
    fun `어휘 밖의 값은 OTHER로 접고 원래 문자열을 상세로 남긴다`() {
        assertThat(reasonOf(6)).isEqualTo("OTHER" to "사이즈가 작아요")
        assertThat(reasonOf(7)).describedAs("공백만 있는 상세는 비어 있는 것으로 봐야 한다").isEqualTo("OTHER" to "BROKEN")
        assertThat(reasonOf(8)).describedAs("이미 적힌 상세는 덮지 않는다").isEqualTo("OTHER" to "구매자가 적어 둔 상세")
    }

    @Test
    fun `유형이 받지 못하는 조합도 OTHER로 접는다`() {
        assertThat(reasonOf(9)).describedAs("교환은 변심을 받지 않는다").isEqualTo("OTHER" to "CHANGE_OF_MIND")
    }

    @Test
    fun `사유가 없던 행은 그대로 비어 있다`() {
        assertThat(reasonOf(10)).isEqualTo(null to null)
    }

    @Test
    fun `이관이 끝나면 남은 사유는 모두 어휘의 이름과 글자까지 같다`() {
        val codes = query(migratedUrl, "SELECT DISTINCT reason_code FROM claims WHERE reason_code IS NOT NULL") { it.getString(1) }
        val names = ClaimReasonCode.entries.map { it.name }

        assertThat(codes).isNotEmpty().allSatisfy { assertThat(it).isIn(names) }
    }

    /** 사유 코드만 보고 반품비 부담을 가르려면 유형이 받을 수 있는 코드만 남아 있어야 한다. */
    @Test
    fun `남은 사유는 모두 그 유형이 받을 수 있는 코드다`() {
        val rows =
            query(migratedUrl, "SELECT claim_type, reason_code FROM claims WHERE reason_code IS NOT NULL") {
                it.getString(1) to it.getString(2)
            }

        assertThat(rows).isNotEmpty().allSatisfy { (claimType, reasonCode) ->
            val selectable = ClaimReasonCode.selectableFor(enumValueOf(claimType)).map { it.name }
            assertThat(reasonCode).describedAs("%s 가 받지 못하는 사유", claimType).isIn(selectable)
        }
    }

    private fun reasonOf(id: Long): Pair<String?, String?> =
        query(migratedUrl, "SELECT reason_code, reason_detail FROM claims WHERE id = $id") {
            it.getString(1) to it.getString(2)
        }.single()

    /**
     * 자유 문자열 시절에 들어갈 수 있던 모양들. claims에는 FK가 없어(V21·V23 규약) 주문 행 없이 심을 수 있고,
     * seller_id·order_id는 V27이 NOT NULL로 좁혀 둔 자리라 더미 값을 채운다.
     */
    private fun legacyClaimRows(): List<String> =
        listOf(
            row(1, "CANCEL", "'CHANGE_OF_MIND'", "'옵션을 잘못 골랐어요'"),
            row(2, "CANCEL", "'change_of_mind'", "'소문자로 심긴 사유'"),
            row(3, "CANCEL", "'  DELIVERY_DELAY '", "NULL"),
            row(4, "RETURN", "'DEFECT'", "NULL"),
            row(5, "CANCEL", "'CHANGE_MIND'", "NULL"),
            row(6, "CANCEL", "'사이즈가 작아요'", "NULL"),
            row(7, "CANCEL", "'BROKEN'", "'   '"),
            row(8, "CANCEL", "'BROKEN'", "'구매자가 적어 둔 상세'"),
            row(9, "EXCHANGE", "'CHANGE_OF_MIND'", "NULL"),
            row(10, "CANCEL", "NULL", "NULL"),
        )

    private fun row(
        id: Long,
        claimType: String,
        reasonCode: String,
        reasonDetail: String,
    ): String =
        """
        INSERT INTO claims (id, public_id, seller_id, order_id, claim_type, reason_code, reason_detail,
                            status, version, requested_at, created_at, updated_at)
        VALUES ($id, 'V33CLAIM${id.toString().padStart(17, '0')}', 7001, 8001, '$claimType', $reasonCode, $reasonDetail,
                'CLAIM_REQUESTED', 0, NOW(6), NOW(6), NOW(6))
        """.trimIndent()

    private fun <T> query(
        url: String,
        sql: String,
        row: (ResultSet) -> T,
    ): List<T> =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                val rs = statement.executeQuery(sql)
                val result = mutableListOf<T>()
                while (rs.next()) result += row(rs)
                result
            }
        }

    private fun execute(
        url: String,
        statements: List<String>,
    ) = connect(url).use { conn ->
        conn.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    private fun recreateDatabase(name: String): String {
        connect(adminUrl(null)).use { conn ->
            conn.createStatement().use {
                it.execute("DROP DATABASE IF EXISTS $name")
                it.execute("CREATE DATABASE $name DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            }
        }
        return adminUrl(name)
    }

    private fun adminUrl(database: String?): String {
        val base = dataSource.connection.use { it.metaData.url }.substringBeforeLast('/')
        return "$base/${database ?: ""}"
    }

    private fun connect(url: String): Connection = java.sql.DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

    private fun flyway(url: String): Flyway = Flyway.configure().dataSource(url, ROOT_USER, ROOT_PASSWORD).load()

    private fun migrateUpTo(
        url: String,
        version: String,
    ) = Flyway
        .configure()
        .dataSource(url, ROOT_USER, ROOT_PASSWORD)
        .target(MigrationVersion.fromVersion(version))
        .load()
        .migrate()

    companion object {
        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
