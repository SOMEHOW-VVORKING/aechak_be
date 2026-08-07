package com.aechak.admin.support;

import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.aechak.websecurity.config.JwtConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 어드민 통합 테스트 공용 베이스 — 역할(role) 클레임을 바꿔가며 게이트를 검증한다.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
        })
@Import(IntegrationTestConfig.class)
public abstract class IntegrationTestBase {

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected JwtEncoder jwtEncoder;

    @Autowired
    private DatabaseCleaner cleaner;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        IntegrationTestConfig.registerDatasource(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        cleaner.truncateAll();
    }

    protected long createUser() {
        return createUser(UserStatus.ACTIVE);
    }

    /** 원하는 상태의 유저를 심고 그 id를 반환한다. 어드민 승격은 role 클레임이 결정하므로 users.role은 손대지 않는다. */
    protected long createUser(UserStatus status) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            em.flush();
            em.createQuery("update User u set u.status = :st where u.id = :id")
                    .setParameter("st", status)
                    .setParameter("id", user.getId())
                    .executeUpdate();
            return user.getId();
        }));
    }

    protected String mintAccessToken(long userId) {
        return mintAccessToken(userId, UserRole.ADMIN);
    }

    /** 주어진 사용자·역할로 자체 RS256 액세스 토큰을 발급한다 — api의 발급 클레임 구조와 동일. */
    protected String mintAccessToken(long userId, UserRole role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, role.name())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
