package com.aechak.api.support

import com.aechak.application.auth.port.SocialTokenVerifier
import com.aechak.application.file.port.FileStorage
import com.aechak.application.user.verification.support.VerificationCodeGenerator
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.social.vo.ProviderUser
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer

/**
 * 통합 테스트 공용 인프라. 데이터 정리기와 외부 어댑터 Fake 빈을 등록한다.
 *
 * 컨테이너는 빈이 아니라 companion에서 직접 start한다(70 §3 예외).
 * 빈으로 등록하면 컨텍스트가 닫힐 때 컨테이너도 함께 멈추는데, 테스트 베이스가
 * 둘(IntegrationTestBase, KafkaIntegrationTestBase)이 된 지금은 한쪽 컨텍스트의
 * 종료가 다른 쪽의 DB를 끊어버릴 수 있기 때문이다. 수동 start한 컨테이너는
 * JVM이 끝날 때 Testcontainers가 정리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    companion object {
        private const val REDIS_PORT = 6379

        // 운영 MySQL 버전 고정 태그. JVM당 1회만 뜨고, 두 베이스의 컨텍스트가 공유한다.
        private val mysql = MySQLContainer("mysql:8.4").apply { start() }

        // 인증 코드와 발송 제한 저장소. MySQL과 같은 이유로 빈이 아니라 수동 start.
        private val redis = GenericContainer("redis:7.4").withExposedPorts(REDIS_PORT).apply { start() }

        /** 각 테스트 베이스의 @DynamicPropertySource가 호출해 datasource와 redis를 이 컨테이너들로 연결한다. */
        @JvmStatic
        fun registerContainers(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
        }
    }

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

    /** 실 어댑터면 펫 사진 승격이 자격증명 없이 AWS로 나감 */
    @Bean
    @Primary
    fun fakeFileStorage(): FileStorage = FakeFileStorage()

    /** 인증번호 확인 단계까지 테스트할 수 있도록 항상 같은 인증번호를 생성한다. */
    @Bean
    @Primary
    fun fixedVerificationCodeGenerator(): VerificationCodeGenerator = VerificationCodeGenerator { "000000" }

    /** 외부 JWKS를 호출하지 않고 "providerId:email" 형식의 idToken을 검증 결과로 변환한다. */
    @Bean
    @Primary
    fun fakeSocialTokenVerifier(): SocialTokenVerifier =
        object : SocialTokenVerifier {
            override fun verify(
                provider: SocialProvider,
                idToken: String,
            ): ProviderUser {
                val parts = idToken.split(":")
                return ProviderUser(provider, parts[0], parts.getOrNull(1)?.ifBlank { null })
            }
        }
}
