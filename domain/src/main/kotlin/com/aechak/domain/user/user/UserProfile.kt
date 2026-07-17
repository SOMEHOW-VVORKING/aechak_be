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

@Entity
@Table(
    name = "user_profiles",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_profiles_nickname", columnNames = ["nickname"])],
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

    @Column(length = 12, nullable = false)
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
        private const val NICKNAME_MAX = 12

        fun of(
            user: User,
            nickname: String,
        ): UserProfile = UserProfile(user, validateNickname(nickname))

        private fun validateNickname(nickname: String): String {
            val trimmed = nickname.trim()
            if (trimmed.isEmpty() || trimmed.length > NICKNAME_MAX) {
                throw BusinessException(UserErrorCode.INVALID_NICKNAME)
            }
            return trimmed
        }
    }
}
