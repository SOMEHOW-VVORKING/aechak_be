package com.aechak.application.sns.facade

import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SnsFacade(
    private val snsProfileService: SnsProfileService,
    private val userUseCase: UserUseCase,
    private val petProfileUseCase: PetProfileUseCase,
) : SnsUseCase {
    @Transactional(readOnly = true)
    override fun getProfile(
        publicId: String,
        viewerId: Long?,
    ): SnsProfileResult = loadProfile(userUseCase.findPublicProfile(publicId), viewerId)

    @Transactional(readOnly = true)
    override fun getMyProfile(userId: Long): SnsProfileResult = loadProfile(userUseCase.findPublicProfileById(userId), userId)

    private fun loadProfile(
        found: UserPublicProfileResult?,
        viewerId: Long?,
    ): SnsProfileResult {
        val target = snsProfileService.requireVisible(found)
        return snsProfileService.toResult(target, viewerId, petProfileUseCase.getPets(target.userId).pets)
    }
}
