package com.aechak.application.sns.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.service.BlockService
import com.aechak.application.sns.service.FollowService
import com.aechak.application.sns.service.MuteService
import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.sns.service.SnsUserListService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.BlockUserCommand
import com.aechak.application.sns.usecase.command.FollowUserCommand
import com.aechak.application.sns.usecase.command.MuteUserCommand
import com.aechak.application.sns.usecase.query.SnsFollowListQuery
import com.aechak.application.sns.usecase.query.SnsRelationListQuery
import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.sns.usecase.result.SnsRelationItemResult
import com.aechak.application.sns.usecase.result.SnsUserItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class SnsFacade(
    private val snsProfileService: SnsProfileService,
    private val followService: FollowService,
    private val blockService: BlockService,
    private val muteService: MuteService,
    private val snsVisibilityService: SnsVisibilityService,
    private val snsUserListService: SnsUserListService,
    private val userUseCase: UserUseCase,
    private val petProfileUseCase: PetProfileUseCase,
    private val fileUseCase: FileUseCase,
) : SnsUseCase {
    @Transactional(readOnly = true)
    override fun getProfile(
        publicId: String,
        viewerId: Long?,
    ): SnsProfileResult = loadProfile(userUseCase.findPublicProfile(publicId), viewerId)

    @Transactional(readOnly = true)
    override fun getMyProfile(userId: Long): SnsProfileResult = loadProfile(userUseCase.findPublicProfileById(userId), userId)

    // 맞는 행이 없는 FOR SHARE가 sns_blocks 갭 잠금으로 다른 쌍의 차단을 막지 않도록 READ_COMMITTED.
    // INSERT IGNORE의 중복 키 검사는 READ_COMMITTED에서도 갭 잠금을 잡는다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun follow(command: FollowUserCommand) {
        val target = snsProfileService.requireVisible(userUseCase.findPublicProfile(command.targetPublicId))
        followService.follow(command.userId, target.userId)
    }

    // 지울 행이 없는 DELETE가 갭 잠금으로 다른 쌍의 팔로우를 막지 않도록 READ_COMMITTED
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun unfollow(command: FollowUserCommand) {
        val target = snsProfileService.requireExists(userUseCase.findPublicProfile(command.targetPublicId))
        followService.unfollow(command.userId, target.userId)
    }

    @Transactional(readOnly = true)
    override fun getFollows(query: SnsFollowListQuery): CursorPageResult<SnsUserItemResult> {
        val owner = snsProfileService.requireVisible(userUseCase.findPublicProfile(query.ownerPublicId))
        val pair = snsVisibilityService.findPairState(query.viewerId, owner.userId)
        snsProfileService.requireNotBlockedByTarget(pair)
        snsProfileService.requireNotBlockedByViewer(pair)
        val hidden = snsVisibilityService.findViewerFilter(query.viewerId)
        val page = snsUserListService.getFollowPage(query, owner.userId, owner.publicId, hidden)
        val userIds = page.items.map { it.userId }
        val followingIds = followService.findFollowingAmong(query.viewerId, userIds)
        val pets = petProfileUseCase.getDefaultPetSummaries(userIds).associateBy { it.userId }
        return page.map {
            val isMe = it.userId == query.viewerId
            SnsUserItemResult.from(
                view = it,
                profileImageUrl = fileUseCase.resolveMediaUrl(it.profileImageKey),
                petSummary = pets[it.userId],
                isMe = isMe,
                following = !isMe && it.userId in followingIds,
            )
        }
    }

    // 지울 팔로우 행이 없는 DELETE가 갭 잠금으로 다른 쌍의 팔로우를 막지 않도록 READ_COMMITTED
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

    @Transactional(readOnly = true)
    override fun getMyRelations(query: SnsRelationListQuery): CursorPageResult<SnsRelationItemResult> {
        val owner = snsProfileService.requireExists(userUseCase.findPublicProfileById(query.userId))
        return snsUserListService.getRelationPage(query, owner.publicId).map {
            SnsRelationItemResult.from(it, fileUseCase.resolveMediaUrl(it.profileImageKey))
        }
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
