package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
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
    uniqueConstraints = [
        UniqueConstraint(name = User.UK_PHONE_HMAC, columnNames = ["phone_hmac"]),
        UniqueConstraint(name = "uk_users_public_id", columnNames = ["public_id"]),
    ],
)
class User protected constructor() : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

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

    /**
     * 임시 정지의 만료 시각 — 임시 정지에만 값이 있다.
     * 만료 판정은 저장값을 되돌리는 배치가 아니라 UserStatus.effective 가 소유한다.
     */
    @Column
    var sanctionUntil: LocalDateTime? = null
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

    /**
     * 프로필이 없는 것이 정상인 상태(온보딩 전·탈퇴)면 null, 그 외는 [profile]이 답한다.
     * 있어야 할 프로필이 없으면 숨기지 않고 터뜨린다 — 조용히 null을 내리면 손상된 계정이 정상으로 보인다.
     */
    val profileIfExpected: UserProfile?
        get() =
            when (effectiveStatus()) {
                UserStatus.PENDING_ONBOARDING, UserStatus.WITHDRAWN -> null
                else -> profile
            }

    /** 프로필 이미지 key */
    val profileImageKey: String?
        get() = _profile?.profileImageKey

    /** 표시용 닉네임 — 프로필이 없으면 null. */
    val nickname: String?
        get() = _profile?.nickname

    /** 소개글 — 프로필이 없으면 null. */
    val bio: String?
        get() = _profile?.bio

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

    /** 프로필 전체 교체 - 이용 가능한 사용자만 프로필을 수정할 수 있다. */
    fun updateProfile(
        nickname: String,
        bio: String?,
        profileImageKey: String?,
    ) {
        requireUsableAccount("프로필 수정")
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
        requireUsableAccount("전화 인증")
        this.phoneNumber = encryptedPhone
        this.phoneHmac = phoneHmac
        this.phoneLast4Hmac = last4Hmac
        this.isPhoneVerified = true
        this.phoneVerifiedAt = LocalDateTime.now()
    }

    /** 일반 회원을 관리자로 승격 */
    fun promoteToAdmin() {
        if (role == UserRole.ADMIN) {
            throw BusinessException(UserErrorCode.USER_ALREADY_ADMIN)
        }
        check(status == UserStatus.ACTIVE) { "관리자 승격은 ACTIVE 상태에서만 가능합니다 (userId=$id, status=$status)" }
        role = UserRole.ADMIN
    }

    /** 관리자를 일반 회원으로 강등 — 이미 일반 회원이면 아무것도 바꾸지 않는다. */
    fun demoteToGeneral() {
        if (role == UserRole.GENERAL) {
            return
        }
        role = UserRole.GENERAL
    }

    /**
     * 접근 판정에 쓸 유효 상태 — 기간이 지난 임시 정지는 ACTIVE 로 본다.
     * 인가 게이트(포트 경유)와 어드민 조회가 같은 판정을 공유한다.
     */
    @JvmOverloads
    fun effectiveStatus(at: LocalDateTime = LocalDateTime.now()): UserStatus = UserStatus.effective(status, sanctionUntil, at)

    /** 이용 제한 — 로그인은 되고 커뮤니티성 쓰기만 막힌다. 기간이 없는 제재라 만료 시각을 비운다. */
    fun restrict() {
        rejectStatusChangeOnClosedAccount()
        rejectReversibleSanctionBeforeOnboarding()
        status = UserStatus.RESTRICTED
        sanctionUntil = null
    }

    /** 임시 정지 — 만료 시각은 미래여야 한다. 과거 시각이면 걸자마자 풀린 정지가 된다. */
    fun suspendUntil(until: LocalDateTime) {
        rejectStatusChangeOnClosedAccount()
        rejectReversibleSanctionBeforeOnboarding()
        if (!until.isAfter(LocalDateTime.now())) {
            throw BusinessException(UserErrorCode.SANCTION_PERIOD_NOT_IN_FUTURE)
        }
        status = UserStatus.SUSPENDED
        sanctionUntil = until
    }

    /** 영구 정지 — 종단 상태. 탈퇴(본인 의사)와 달리 프로필·전화 점유는 그대로 둬 재가입 우회를 막는다. */
    fun ban() {
        rejectStatusChangeOnClosedAccount()
        status = UserStatus.BANNED
        sanctionUntil = null
    }

    /** 운영자의 탈퇴 처리 — 상태 전이는 본인 탈퇴와 같고, 운영 조치였다는 사실은 제재 이력이 남긴다. */
    fun forceWithdraw() {
        rejectStatusChangeOnClosedAccount()
        withdraw()
    }

    /** 제재 해제 — 걸려 있는 제재가 없으면 거부한다. 기간이 지난 임시 정지도 저장값 정리를 위해 해제할 수 있다. */
    fun releaseSanction() {
        if (status != UserStatus.RESTRICTED && status != UserStatus.SUSPENDED) {
            throw BusinessException(UserErrorCode.NO_ACTIVE_SANCTION)
        }
        status = UserStatus.ACTIVE
        sanctionUntil = null
    }

    /** 탈퇴·영구 정지는 운영 조치로 더 건드리지 않는다 — 둘 다 종단 상태다. */
    private fun rejectStatusChangeOnClosedAccount() {
        if (status == UserStatus.WITHDRAWN) {
            throw BusinessException(UserErrorCode.ALREADY_WITHDRAWN)
        }
        if (status == UserStatus.BANNED) {
            throw BusinessException(UserErrorCode.PERMANENT_BAN_IS_FINAL)
        }
    }

    /**
     * 해제·만료로 되돌아오는 제재(이용 제한·임시 정지)는 온보딩을 마친 계정에만 걸 수 있다.
     * 상태가 한 축이라 PENDING_ONBOARDING을 덮으면 돌아올 상태를 잃고 온보딩을 건너뛴 ACTIVE가 된다.
     */
    private fun rejectReversibleSanctionBeforeOnboarding() {
        if (status == UserStatus.PENDING_ONBOARDING) {
            throw BusinessException(UserErrorCode.SANCTION_REQUIRES_ONBOARDED_ACCOUNT)
        }
    }

    /** 자기 정보를 다루는 경로의 전제 — 이용 제한은 커뮤니티성 쓰기만 막는 제재라 통과한다. */
    private fun requireUsableAccount(action: String) {
        val effective = effectiveStatus()
        check(effective == UserStatus.ACTIVE || effective == UserStatus.RESTRICTED) {
            "${action}은 이용 가능한 계정에서만 가능합니다 (userId=$id, status=$status)"
        }
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
