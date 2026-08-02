package com.aechak.domain.user.pet

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.pet.enums.PetStatus
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
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
import jakarta.persistence.Version
import java.math.BigDecimal

@Entity
@Table(name = "pet_profiles")
class PetProfile protected constructor(
    user: User,
    breed: Breed,
    name: String,
    birthYearMonth: String?,
    weight: BigDecimal?,
    profileImageKey: String?,
    isDefault: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breed_id", nullable = false)
    var breed: Breed = breed
        protected set

    val species: Species get() = breed.species

    @Column(length = 50, nullable = false)
    var name: String = name
        protected set

    @Column(length = 7)
    var birthYearMonth: String? = birthYearMonth
        protected set

    @Column(precision = 4, scale = 1)
    var weight: BigDecimal? = weight
        protected set

    @Column(length = 1024)
    var profileImageKey: String? = profileImageKey
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: PetStatus = PetStatus.ACTIVE
        protected set

    @Column(nullable = false)
    var isDefault: Boolean = isDefault
        protected set

    @Version
    @Column(nullable = false)
    var version: Int = 0
        protected set

    fun delete() {
        status = PetStatus.DELETED
        isDefault = false
    }

    fun releaseDefault() {
        isDefault = false
    }

    fun markAsDefault() {
        isDefault = true
    }

    /** 전체 객체 전송이라 통째로 덮음. `profileImageKey`가 null이면 사진이 지워짐 */
    fun update(
        breed: Breed,
        name: String,
        birthYearMonth: String?,
        weight: BigDecimal?,
        profileImageKey: String?,
    ) {
        // 종은 등록 시 확정이다. 파생 이후로는 품종을 바꾸면 종까지 따라 바뀌므로 여기서 막는다.
        if (breed.species != species) {
            throw BusinessException(UserErrorCode.INVALID_BREED)
        }
        validate(breed, weight)
        this.breed = breed
        this.name = name
        this.birthYearMonth = birthYearMonth
        this.weight = weight
        this.profileImageKey = profileImageKey
    }

    fun requireVersion(expected: Int) {
        if (version != expected) {
            throw BusinessException(UserErrorCode.PET_PROFILE_VERSION_CONFLICT)
        }
    }

    companion object {
        private val MIN_WEIGHT = BigDecimal("0.1")
        private val MAX_WEIGHT = BigDecimal("200.0")

        private fun validateWeight(weight: BigDecimal?) {
            if (weight != null && (weight < MIN_WEIGHT || weight > MAX_WEIGHT)) {
                throw BusinessException(UserErrorCode.INVALID_PET_WEIGHT)
            }
        }

        fun validate(
            breed: Breed,
            weight: BigDecimal?,
        ) {
            validateWeight(weight)
        }

        fun register(
            user: User,
            breed: Breed,
            name: String,
            birthYearMonth: String? = null,
            weight: BigDecimal? = null,
            profileImageKey: String? = null,
            isDefault: Boolean = false,
        ): PetProfile {
            validate(breed, weight)
            return PetProfile(user, breed, name, birthYearMonth, weight, profileImageKey, isDefault)
        }
    }
}
