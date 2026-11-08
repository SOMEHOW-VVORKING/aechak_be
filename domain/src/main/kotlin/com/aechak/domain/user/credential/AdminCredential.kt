package com.aechak.domain.user.credential

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 어드민 비밀번호 로그인 자격. 소셜 자격(SocialIdentity)과 병존하고 한 계정이 둘 다 가질 수 있다.
 *
 * 평문 비밀번호는 이 엔티티에 들어오지 않는다. 대조는 해시끼리 하고 그 해싱은 포트 뒤에 있다 —
 * 도메인이 알고리즘을 알면 교체할 때 도메인을 고쳐야 한다.
 */
@Entity
@Table(
    name = "admin_credentials",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_admin_credentials_login_id", columnNames = ["login_id"]),
        UniqueConstraint(name = "uk_admin_credentials_user_id", columnNames = ["user_id"]),
    ],
)
class AdminCredential protected constructor(
    user: User,
    loginId: String,
    passwordHash: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @Column(name = "login_id", length = 50, nullable = false)
    val loginId: String = loginId

    @Column(name = "password_hash", length = 100, nullable = false)
    var passwordHash: String = passwordHash
        protected set

    /** 비밀번호 교체 — 해시만 받는다. 평문을 받으면 도메인이 해싱 알고리즘을 알아야 한다. */
    fun changePasswordHash(hash: String) {
        passwordHash = hash
    }

    companion object {
        fun issue(
            user: User,
            loginId: String,
            passwordHash: String,
        ): AdminCredential = AdminCredential(user, loginId, passwordHash)
    }
}
