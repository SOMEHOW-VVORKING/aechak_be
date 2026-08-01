package com.aechak.application.user.pet.service

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.aechak.application.user.pet.usecase.command.UpdatePetProfileCommand
import com.aechak.application.user.user.service.UserService
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.repository.BreedRepository
import com.aechak.domain.user.pet.repository.PetProfileRepository
import org.springframework.stereotype.Service

@Service
class PetProfileService(
    private val petProfileRepository: PetProfileRepository,
    private val breedRepository: BreedRepository,
    private val userService: UserService,
    private val fileUseCase: FileUseCase,
) {
    fun getActivePets(userId: Long): List<PetProfile> = petProfileRepository.findAllActiveByUserIdDefaultFirst(userId)

    fun register(command: RegisterPetProfileCommand): PetProfile {
        // 동시 요청 둘이 한도를 각각 통과할 수 있음. 문제 시 비관적 락.
        val activeCount = petProfileRepository.countActiveByUserId(command.userId)
        if (activeCount >= MAX_ACTIVE_PETS) {
            throw BusinessException(UserErrorCode.PET_PROFILE_LIMIT_EXCEEDED)
        }

        val user = userService.getById(command.userId)
        val breed = loadBreed(command.breedId)
        val birthYearMonth = BirthYearMonthNormalizer.normalize(command.birthYearMonth)
        // 승격보다 먼저. 승격은 S3 복사라 롤백이 안 되고, 정식 접두엔 만료 규칙이 없어 회수 불가.
        PetProfile.validate(breed, command.weight)

        val profileImageKey = promoteImageIfPresent(command.userId, command.profileImageKey)

        if (command.isDefault) {
            releaseOtherDefaults(command.userId, exceptId = null)
        }

        val pet = command.toEntity(user, breed, birthYearMonth, profileImageKey)
        if (activeCount == 0L) {
            pet.markAsDefault() // 기본 없는 상태를 안 만들려고 첫 펫은 요청값 무시
        }
        return petProfileRepository.save(pet)
    }

    fun update(command: UpdatePetProfileCommand): PetProfile {
        val pet = loadOwnedActive(command.userId, command.petId)
        command.version?.let(pet::requireVersion)

        val breed = loadBreed(command.breedId)
        val birthYearMonth = BirthYearMonthNormalizer.normalize(command.birthYearMonth)
        // 등록과 같은 이유로 승격 전에 검증.
        PetProfile.validate(breed, command.weight)

        val imageKey =
            command.profileImageKey?.let {
                fileUseCase.promoteIfTmp(PromoteFileCommand(it, command.userId, UploadPurpose.PET_PROFILE)).key
            }
        pet.update(
            breed = breed,
            name = command.name,
            birthYearMonth = birthYearMonth,
            weight = command.weight,
            profileImageKey = imageKey,
        )
        if (command.isDefault) {
            promoteToDefault(command.userId, pet)
        }
        return petProfileRepository.save(pet)
    }

    /** 기본을 지웠으면 남은 펫 하나를 승격하고 그 id를 돌려줌 */
    fun delete(
        userId: Long,
        petId: Long,
    ): Long? {
        val pet = loadOwnedActive(userId, petId)
        val wasDefault = pet.isDefault
        pet.delete()
        petProfileRepository.save(pet)
        if (!wasDefault) return null

        val promoted =
            petProfileRepository
                .findAllActiveByUserIdRecentFirst(userId)
                .firstOrNull { it.id != petId } // flush가 늦어도 방금 지운 건 id로 거른다
                ?: return null
        promoted.markAsDefault()
        petProfileRepository.save(promoted)
        return promoted.id
    }

    private fun loadOwnedActive(
        userId: Long,
        petId: Long,
    ): PetProfile {
        val pet =
            petProfileRepository.findActiveById(petId)
                ?: throw BusinessException(UserErrorCode.PET_PROFILE_NOT_FOUND)
        if (pet.user.id != userId) {
            throw BusinessException(UserErrorCode.PET_PROFILE_ACCESS_DENIED)
        }
        return pet
    }

    private fun promoteToDefault(
        userId: Long,
        pet: PetProfile,
    ) {
        releaseOtherDefaults(userId, exceptId = pet.id) // 바로 다시 올릴 대상이라 UPDATE 한 번을 아낌
        pet.markAsDefault()
    }

    /** 완벽한 동시성 방어는 불가능하지만, 불필요하다고 판단함 */
    private fun releaseOtherDefaults(
        userId: Long,
        exceptId: Long?,
    ) {
        petProfileRepository
            .findAllActiveByUserIdRecentFirst(userId)
            .filter { it.isDefault && it.id != exceptId }
            .forEach {
                it.releaseDefault()
                petProfileRepository.save(it)
            }
    }

    private fun loadBreed(breedId: Long): Breed =
        breedRepository.findById(breedId)
            ?: throw BusinessException(UserErrorCode.INVALID_BREED)

    private fun promoteImageIfPresent(
        userId: Long,
        tmpKey: String?,
    ): String? {
        if (tmpKey == null) return null
        return fileUseCase.promote(PromoteFileCommand(tmpKey, userId, UploadPurpose.PET_PROFILE)).key
    }

    companion object {
        const val MAX_ACTIVE_PETS = 10
    }
}
