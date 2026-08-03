package com.aechak.api.support

import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.websecurity.config.JwtConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 모든 통합 테스트의 공용 베이스
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
    ],
)
@Import(IntegrationTestConfig::class)
abstract class IntegrationTestBase {
    @PersistenceContext
    protected lateinit var em: EntityManager

    @Autowired
    protected lateinit var tx: TransactionTemplate

    @Autowired
    protected lateinit var jwtEncoder: JwtEncoder

    @Autowired
    private lateinit var cleaner: DatabaseCleaner

    @BeforeEach
    fun cleanDatabase() = cleaner.truncateAll()

    /** ACTIVE 유저를 심고 그 id를 반환한다. 인증이 필요한 통합 테스트 공용. */
    protected fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    /** 주어진 사용자로 자체 RS256 액세스 토큰을 발급한다. */
    protected fun mintAccessToken(userId: Long): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, "GENERAL")
                .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
