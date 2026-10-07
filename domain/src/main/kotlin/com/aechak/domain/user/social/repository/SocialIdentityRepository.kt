package com.aechak.domain.user.social.repository

import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider

interface SocialIdentityRepository {
    fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity?

    fun save(identity: SocialIdentity): SocialIdentity

    /** 사용자의 첫 번째 소셜 연결을 조회 */
    fun findByUserId(userId: Long): SocialIdentity?

    fun delete(identity: SocialIdentity)

    fun flush()
}
