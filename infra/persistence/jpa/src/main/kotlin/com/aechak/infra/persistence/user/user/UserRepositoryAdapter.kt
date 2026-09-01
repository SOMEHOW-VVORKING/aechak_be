package com.aechak.infra.persistence.user.user

import com.aechak.application.user.user.port.view.UserAuthorView
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface UserJpaRepository : JpaRepository<User, Long> {
    /** 인가 상태검증용 프로젝션 — 엔티티(+eager 프로필) 로딩 없이 status만 읽는다. */
    @Query("select u.status from User u where u.id = :id")
    fun findStatusById(
        @Param("id") id: Long,
    ): UserStatus?

    /** 작성자 표시용 배치 프로젝션 — 프로필 없어도 포함되도록 left join. */
    @Query(
        "select new com.aechak.application.user.user.port.view.UserAuthorView(u.id, u.status, p.nickname, p.profileImageKey) " +
            "from User u left join UserProfile p on p.userId = u.id where u.id in :ids",
    )
    fun findAuthorsByIds(
        @Param("ids") ids: Collection<Long>,
    ): List<UserAuthorView>

    /** 닉네임 선점 검사 — 비교는 nickname 컬럼 collation(utf8mb4 ci)을 그대로 탄다. */
    @Query("select count(p) > 0 from UserProfile p where p.nickname = :nickname and p.userId <> :excludeUserId")
    fun existsNickname(
        @Param("nickname") nickname: String,
        @Param("excludeUserId") excludeUserId: Long,
    ): Boolean

    fun findByPhoneHmac(phoneHmac: ByteArray): User?

    @Modifying
    @Query(
        "update User u " +
            "set u.pointBalance = u.pointBalance - :amount, u.updatedAt = :now " +
            "where u.id = :userId and u.pointBalance >= :amount",
    )
    fun deductPointBalance(
        @Param("userId") userId: Long,
        @Param("amount") amount: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "update User u " +
            "set u.pointBalance = u.pointBalance + :amount, u.updatedAt = :now " +
            "where u.id = :userId",
    )
    fun addPointBalance(
        @Param("userId") userId: Long,
        @Param("amount") amount: Long,
        @Param("now") now: LocalDateTime,
    ): Int
}

/**
 * domain 포트(UserRepository)의 JPA 어댑터 — 각 도메인이 따라갈 어댑터 템플릿.
 * 포트 시그니처(도메인 타입)를 그대로 구현한다 — Pageable 등 Spring 타입을 포트로 역류시키지 않는다.
 */
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findById(id: Long): User? = jpaRepository.findByIdOrNull(id)

    override fun save(user: User): User = jpaRepository.save(user)

    override fun isNicknameTaken(
        nickname: String,
        excludeUserId: Long,
    ): Boolean = jpaRepository.existsNickname(nickname, excludeUserId)

    override fun findByPhoneHmac(phoneHmac: ByteArray): User? = jpaRepository.findByPhoneHmac(phoneHmac)

    override fun flush() = jpaRepository.flush()

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun deductPointBalance(
        userId: Long,
        amount: Long,
    ): Boolean = jpaRepository.deductPointBalance(userId, amount, LocalDateTime.now()) == 1

    override fun addPointBalance(
        userId: Long,
        amount: Long,
    ): Boolean = jpaRepository.addPointBalance(userId, amount, LocalDateTime.now()) == 1
}
