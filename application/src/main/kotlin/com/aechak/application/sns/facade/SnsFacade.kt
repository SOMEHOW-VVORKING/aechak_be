package com.aechak.application.sns.facade

import com.aechak.application.sns.service.BlockService
import com.aechak.application.sns.service.MuteService
import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.BlockUserCommand
import com.aechak.application.sns.usecase.command.MuteUserCommand
import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class SnsFacade(
    private val snsProfileService: SnsProfileService,
    private val blockService: BlockService,
    private val muteService: MuteService,
    private val snsVisibilityService: SnsVisibilityService,
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun block(command: BlockUserCommand) {
        val target = snsProfileService.requireVisible(userUseCase.findPublicProfile(command.targetPublicId))
        blockService.block(command.userId, target.userId)
    }

    // 없는 쌍의 DELETE가 갭 잠금으로 다른 쌍의 차단을 막지 않도록 READ_COMMITTED
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun unblock(command: BlockUserCommand) {
        val target = snsProfileService.requireExists(userUseCase.findPublicProfile(command.targetPublicId))
        blockService.unblock(command.userId, target.userId)
    }

    // 뮤트는 통계 행을 잠그지 않는다. 갭 잠금을 줄이려고 차단과 같이 READ_COMMITTED
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun mute(command: MuteUserCommand) {
        val target = snsProfileService.requireVisible(userUseCase.findPublicProfile(command.targetPublicId))
        muteService.mute(command.userId, target.userId)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun unmute(command: MuteUserCommand) {
        val target = snsProfileService.requireExists(userUseCase.findPublicProfile(command.targetPublicId))
        muteService.unmute(command.userId, target.userId)
    }

    private fun loadProfile(
        found: UserPublicProfileResult?,
        viewerId: Long?,
    ): SnsProfileResult {
        val target = snsProfileService.requireVisible(found)
        val pair = snsVisibilityService.findPairState(viewerId, target.userId)
        snsProfileService.requireNotBlockedByTarget(pair)
        if (pair.blockedByMe) return snsProfileService.toBlockedResult(target, pair)
        return snsProfileService.toResult(target, viewerId, pair, petProfileUseCase.getPets(target.userId).pets)
    }
}
