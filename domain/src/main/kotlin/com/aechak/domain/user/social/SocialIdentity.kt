package com.aechak.domain.user.social

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.user.User

@Entity
@Table(
    name = "social_identities",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_social_identities_provider", columnNames = ["provider", "provider_id"]),
    ],
)
class SocialIdentity protected constructor(
    user: User,
    provider: SocialProvider,
    providerId: String,
    email: String?,
    refreshTokenEnc: ByteArray?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val provider: SocialProvider = provider

    @Column(name = "provider_id", length = 255, nullable = false)
    val providerId: String = providerId

    @Column(length = 320)
    var email: String? = email
        protected set

    @Column(nullable = false)
    val linkedAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "refresh_token_enc", length = 512)
    var refreshTokenEnc: ByteArray? = refreshTokenEnc
        protected set


    companion object {
        fun link(
            user: User,
            provider: SocialProvider,
            providerId: String,
            email: String? = null,
            refreshTokenEnc: ByteArray? = null,
        ): SocialIdentity = SocialIdentity(user, provider, providerId, email, refreshTokenEnc)
    }
}
