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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = User.UK_PHONE_HMAC, columnNames = ["phone_hmac"])],
)
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

    /** 전체 번호 HMAC blind index — "이 번호로 인증된 계정 찾기"의 검색 키이자 1번호 1계정의 최후방어(UNIQUE). */
    @Column(columnDefinition = "binary(32)")
    var phoneHmac: ByteArray? = null
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

    /** 프로필 이미지 key */
    val profileImageKey: String?
        get() = _profile?.profileImageKey

    /** 사용자를 탈퇴 상태로 바꾸고 닉네임과 전화번호 값을 삭제해 UNIQUE 제약에서 해제 */
    fun withdraw() {
        if (status == UserStatus.WITHDRAWN) {
            throw BusinessException(UserErrorCode.ALREADY_WITHDRAWN)
        }
        status = UserStatus.WITHDRAWN
        withdrawnAt = LocalDateTime.now()
        _profile = null
        unverifyPhone()
    }

    /** 프로필을 생성하고 사용자를 ACTIVE 상태로 변경 */
    fun completeOnboarding(nickname: String) {
        check(status == UserStatus.PENDING_ONBOARDING) { "온보딩 완료 전이는 PENDING 상태에서만 가능합니다 (userId=$id)" }
        _profile = UserProfile.of(this, nickname)
        status = UserStatus.ACTIVE
    }

    /** 프로필 전체 교체 - ACTIVE 사용자만 프로필을 수정할 수 있다. */
    fun updateProfile(
        nickname: String,
        bio: String?,
        profileImageKey: String?,
    ) {
        check(status == UserStatus.ACTIVE) { "프로필 수정은 ACTIVE 상태에서만 가능합니다 (userId=$id, status=$status)" }
        profile.updateProfile(nickname, bio, profileImageKey)
    }

    /**
     * 전화 인증
     * 전화번호 암호문, 검색용 해시, 인증 상태를 함께 저장
     */
    fun verifyPhone(
        encryptedPhone: ByteArray,
        phoneHmac: ByteArray,
        last4Hmac: ByteArray,
    ) {
        check(status == UserStatus.ACTIVE) { "전화 인증은 ACTIVE 상태에서만 가능합니다 (userId=$id, status=$status)" }
        this.phoneNumber = encryptedPhone
        this.phoneHmac = phoneHmac
        this.phoneLast4Hmac = last4Hmac
        this.isPhoneVerified = true
        this.phoneVerifiedAt = LocalDateTime.now()
    }

    /** 다른 계정으로 전화번호를 이전하거나 탈퇴할 때 저장된 전화번호 정보를 삭제 */
    fun unverifyPhone() {
        phoneNumber = null
        phoneHmac = null
        phoneLast4Hmac = null
        isPhoneVerified = false
        phoneVerifiedAt = null
    }

    companion object {
        /** 전화번호 점유 UNIQUE 제약명 — @Table 선언과 커밋 시점 예외 번역(제약명 분기)이 공유한다. */
        const val UK_PHONE_HMAC = "uk_users_phone_hmac"

        /** 소셜 가입 진입점 — PENDING_ONBOARDING으로 시작하고 프로필(닉네임)은 온보딩에서 생성한다. */
        fun preRegister(): User = User()
    }
}
