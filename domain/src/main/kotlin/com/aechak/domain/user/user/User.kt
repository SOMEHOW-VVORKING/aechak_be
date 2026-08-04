package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
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

    /**
     * 온보딩 완료 — 프로필(닉네임) 생성과 ACTIVE 전이는 반드시 함께 일어난다.
     * 필수 동의 검증은 호출자(application)의 책임 — "ACTIVE ⇒ 필수 동의 존재" 게이트를 통과한 뒤에만 부른다.
     */
    fun completeOnboarding(nickname: String) {
        check(status == UserStatus.PENDING_ONBOARDING) { "온보딩 완료 전이는 PENDING 상태에서만 가능합니다 (userId=$id)" }
        _profile = UserProfile.of(this, nickname)
        status = UserStatus.ACTIVE
    }

    /** 프로필 전체 교체 — ACTIVE에서만. 비ACTIVE 도달은 UserStatusFilter가 걸렀어야 할 방어선 이상이라 500이 맞다. */
    fun updateProfile(
        nickname: String,
        bio: String?,
        profileImageKey: String?,
    ) {
        check(status == UserStatus.ACTIVE) { "프로필 수정은 ACTIVE 상태에서만 가능합니다 (userId=$id, status=$status)" }
        profile.updateProfile(nickname, bio, profileImageKey)
    }

    companion object {
        /** 소셜 가입 진입점 — PENDING_ONBOARDING으로 시작하고 프로필(닉네임)은 온보딩에서 생성한다. */
        fun preRegister(): User = User()
    }
}
