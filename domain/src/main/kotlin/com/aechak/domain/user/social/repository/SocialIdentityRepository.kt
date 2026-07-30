package com.aechak.domain.user.social.repository

import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider

interface SocialIdentityRepository {
    fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity?

    fun save(identity: SocialIdentity): SocialIdentity

    /** 유저의 소셜 연결 — MVP는 유저당 1개 전제, 복수면 최초 연결분. */
    fun findByUserId(userId: Long): SocialIdentity?
}
