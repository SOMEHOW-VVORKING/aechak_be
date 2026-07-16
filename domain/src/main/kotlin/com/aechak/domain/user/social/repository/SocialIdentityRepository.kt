package com.aechak.domain.user.social.repository

import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider

interface SocialIdentityRepository {
    fun findByProviderAndProviderId(
        provider: SocialProvider,
        providerId: String,
    ): SocialIdentity?

    fun save(identity: SocialIdentity): SocialIdentity
}
