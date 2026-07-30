package com.aechak.application.user.pet.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.user.pet.service.PetProfileService
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.aechak.application.user.pet.usecase.result.PetProfileListResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.application.user.pet.usecase.result.RegisterPetProfileResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PetProfileFacade(
    private val petProfileService: PetProfileService,
    private val fileUseCase: FileUseCase,
) : PetProfileUseCase {
    @Transactional
    override fun registerPet(command: RegisterPetProfileCommand): RegisterPetProfileResult {
        val pet = petProfileService.register(command)
        return RegisterPetProfileResult.from(pet, fileUseCase.resolveMediaUrl(pet.profileImageKey))
    }

    @Transactional(readOnly = true)
    override fun getPets(userId: Long): PetProfileListResult =
        PetProfileListResult(
            petProfileService.getActivePets(userId).map {
                PetProfileResult.from(it, fileUseCase.resolveMediaUrl(it.profileImageKey))
            },
        )
}
