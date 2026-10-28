package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.user.user.enums.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * V58(탈퇴한 사용자의 승인 전 입점 신청 소급 취소)이 어떤 행을 바꾸는지 본다.
 * 깨지면 탈퇴한 계정의 신청이 승인 대기로 남거나 탈퇴하지 않은 사용자의 신청까지 취소된다.
 * 컨텍스트를 새로 띄우지 않으려고 공용 베이스를 쓴다. V58은 별도 DB에 적용한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V58CancelWithdrawnApplicationsMigrationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var migratedUrl: String

    @BeforeAll
    fun migrateSeededRows() {
        migratedUrl = recreateDatabase("v58_cancel")
        // users는 Flyway가 만들지 않는다. V52까지 적용한 뒤 V58이 읽는 컬럼만 가진 users를 만들어 심는다
        migrateUpTo(migratedUrl, "52")
        execute(migratedUrl, listOf("CREATE TABLE users (id BIGINT NOT NULL PRIMARY KEY, status VARCHAR(30) NOT NULL)") + seedRows())
        flyway(migratedUrl).migrate()
    }

    @Test
    fun `탈퇴한 사용자의 승인 전 신청만 CANCELLED가 되고 version과 updated_at이 바뀐다`() {
        cases().forEach { (userId, userStatus, applicationStatus) ->
            val (status, version, updatedAt) = applicationOf(userId)
            val cancelled = userStatus == UserStatus.WITHDRAWN && applicationStatus !in KEPT_FOR_WITHDRAWN
            val description = "$userStatus 사용자의 $applicationStatus 신청"

            assertThat(status).describedAs(description).isEqualTo(if (cancelled) "CANCELLED" else applicationStatus.name)
            assertThat(version).describedAs(description).isEqualTo(if (cancelled) SEEDED_VERSION + 1 else SEEDED_VERSION)
            if (cancelled) {
                assertThat(updatedAt).describedAs(description).isAfter(SEEDED_AT)
            } else {
                assertThat(updatedAt).describedAs(description).isEqualTo(SEEDED_AT)
            }
        }
    }

    /** 사용자 상태와 신청 상태의 모든 조합. seller_applications.user_id가 UNIQUE라 조합마다 사용자를 따로 둔다 */
    private fun cases(): List<Triple<Long, UserStatus, ApplicationStatus>> =
        UserStatus.entries
            .flatMap { userStatus -> ApplicationStatus.entries.map { userStatus to it } }
            .mapIndexed { index, (userStatus, applicationStatus) -> Triple(index + 1L, userStatus, applicationStatus) }

    private fun seedRows(): List<String> =
        cases().flatMap { (userId, userStatus, applicationStatus) ->
            listOf(
                "INSERT INTO users (id, status) VALUES ($userId, '${userStatus.name}')",
                """
                INSERT INTO seller_applications (user_id, business_type, status, version, created_at, updated_at)
                VALUES ($userId, 'PERSONAL_GENERAL', '${applicationStatus.name}', $SEEDED_VERSION, '$SEEDED_AT', '$SEEDED_AT')
                """.trimIndent(),
            )
        }

    private fun applicationOf(userId: Long): Triple<String, Long, Timestamp> =
        query(migratedUrl, "SELECT status, version, updated_at FROM seller_applications WHERE user_id = $userId") {
            Triple(it.getString(1), it.getLong(2), it.getTimestamp(3))
        }.single()

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

        private const val SEEDED_VERSION = 7L
        private val SEEDED_AT: Timestamp = Timestamp.valueOf("2026-01-01 00:00:00")

        // 탈퇴한 사용자라도 이미 끝난 신청은 그대로 둔다
        private val KEPT_FOR_WITHDRAWN = setOf(ApplicationStatus.APPROVED, ApplicationStatus.CANCELLED)
    }
}
