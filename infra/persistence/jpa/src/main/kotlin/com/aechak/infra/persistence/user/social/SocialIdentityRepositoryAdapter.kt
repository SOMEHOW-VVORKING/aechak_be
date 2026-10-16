package com.aechak.infra.persistence.user.social

import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.social.repository.SocialIdentityRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SocialIdentityJpaRepository : JpaRepository<SocialIdentity, Long> {
    fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity?

    fun findFirstByUserIdOrderByIdAsc(userId: Long): SocialIdentity?
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

    override fun findByUserId(userId: Long): SocialIdentity? = jpaRepository.findFirstByUserIdOrderByIdAsc(userId)

    override fun delete(identity: SocialIdentity) = jpaRepository.delete(identity)

    override fun flush() = jpaRepository.flush()
}
