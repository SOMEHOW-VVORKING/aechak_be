package com.aechak.domain.user.user

import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.common.error.BusinessException
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.domain.user.user.enums.UserRole

@Entity
@Table(name = "users")
class User protected constructor() : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: UserStatus = UserStatus.PENDING_ONBOARDING
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var role: UserRole = UserRole.GENERAL
        protected set

    /** 비정규화 캐시 — SoT는 point_transactions, 직접 증감 금지. */
    @Column(nullable = false)
    var pointBalance: Long = 0
        protected set

    @Column(length = 255)
    var phoneNumber: ByteArray? = null
        protected set

    @Column(columnDefinition = "binary(32)")
    var phoneLast4Hmac: ByteArray? = null
        protected set

    @Column(nullable = false)
    var isPhoneVerified: Boolean = false
        protected set

    @Column
    var phoneVerifiedAt: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    var warningCount: Int = 0
        protected set

    @Column
    var withdrawnAt: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    var isPiiPurged: Boolean = false
        protected set

    /** LAZY 선언이지만 nullable inverse OneToOne이라 실제 eager. */
    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private var _profile: UserProfile? = null

    val profile: UserProfile
        get() = _profile ?: throw IllegalStateException("프로필이 로딩/생성되지 않았습니다 (userId=$id)")

    fun withdraw() {
        if (status == UserStatus.WITHDRAWN) {
            throw BusinessException(UserErrorCode.ALREADY_WITHDRAWN)
        }
        status = UserStatus.WITHDRAWN
        withdrawnAt = LocalDateTime.now()
    }

    companion object {
        fun register(nickname: String): User {
            val user = User()
            user._profile = UserProfile.of(user, nickname)
            return user
        }
    }
}
