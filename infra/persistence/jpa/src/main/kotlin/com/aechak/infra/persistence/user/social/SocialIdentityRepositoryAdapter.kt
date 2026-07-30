package com.aechak.infra.persistence.user.social

import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.social.repository.SocialIdentityRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SocialIdentityJpaRepository : JpaRepository<SocialIdentity, Long> {
    fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity?
}

@Repository
class SocialIdentityRepositoryAdapter(
    private val jpaRepository: SocialIdentityJpaRepository,
) : SocialIdentityRepository {
    override fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity? = jpaRepository.findByProviderAndProviderId(provider, providerId)

    override fun save(identity: SocialIdentity): SocialIdentity = jpaRepository.save(identity)
}
