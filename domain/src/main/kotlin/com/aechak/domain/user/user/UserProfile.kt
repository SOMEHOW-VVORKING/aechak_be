package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.error.UserErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.text.Normalizer

@Entity
@Table(
    name = "user_profiles",
    uniqueConstraints = [UniqueConstraint(name = UserProfile.UK_NICKNAME, columnNames = ["nickname"])],
)
class UserProfile protected constructor(
    user: User,
    nickname: String,
) : BaseEntity() {
    @Id
    val userId: Long = 0L

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User = user

    // 대소문자 무시 UNIQUE("Coco"="coco" 충돌)를 컬럼 선언으로 명시 — DB 기본 collation 의존 제거
    @Column(columnDefinition = "varchar(12) collate utf8mb4_0900_ai_ci not null")
    var nickname: String = nickname
        protected set

    @Column(length = 1024)
    var profileImageKey: String? = null
        protected set

    @Column(length = 1000)
    var bio: String? = null
        protected set

    @Version
    @Column(nullable = false)
    var version: Int = 0
        protected set

    fun rename(nickname: String) {
        this.nickname = validateNickname(nickname)
    }

    companion object {
        /** 닉네임 UNIQUE 제약명 — @Table 선언과 커밋 시점 예외 번역(제약명 분기)이 공유한다. */
        const val UK_NICKNAME = "uk_user_profiles_nickname"

        /** MySQL 중복 키 메시지의 PK 식별자 — 더블탭(같은 유저 동시 생성) 충돌 판별용. */
        const val PK_CONFLICT_MARKER = "user_profiles.PRIMARY"

        /** 한글(완성형)·영문·숫자 2~12자 — 공백·이모지·자모 불가. */
        private val NICKNAME_PATTERN = Regex("^[가-힣a-zA-Z0-9]{2,12}$")

        fun of(
            user: User,
            nickname: String,
        ): UserProfile = UserProfile(user, validateNickname(nickname))

        /** NFC 정규화 후 형식 판정 — 조합형(NFD) 입력도 완성형으로 통일해 저장·비교한다. */
        fun validateNickname(nickname: String): String {
            val normalized = Normalizer.normalize(nickname.trim(), Normalizer.Form.NFC)
            if (!NICKNAME_PATTERN.matches(normalized)) {
                throw BusinessException(UserErrorCode.INVALID_NICKNAME)
            }
            return normalized
        }
    }
}
