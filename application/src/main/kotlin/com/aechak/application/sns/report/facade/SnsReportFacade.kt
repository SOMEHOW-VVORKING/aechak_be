package com.aechak.application.sns.report.facade

import com.aechak.application.sns.report.service.PostReportService
import com.aechak.application.sns.report.usecase.SnsReportUseCase
import com.aechak.application.sns.report.usecase.command.SubmitPostReportCommand
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.user.report.usecase.UserReportUseCase
import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand
import com.aechak.application.user.user.usecase.UserUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SnsReportFacade(
    private val postAccessService: PostAccessService,
    private val postReportService: PostReportService,
    private val snsProfileService: SnsProfileService,
    private val userUseCase: UserUseCase,
    private val userReportUseCase: UserReportUseCase,
) : SnsReportUseCase {
    // 게시물 상세와 같은 판정으로 거른다. 뮤트한 작성자의 게시물은 상세로 볼 수 있어 신고도 받는다
    @Transactional
    override fun submitPostReport(command: SubmitPostReportCommand) {
        val post = postAccessService.requirePublished(command.postPublicId)
        postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        postAccessService.requireNotBlocked(post.authorId, command.reporterId)
        postReportService.report(command, post)
    }

    // 프로필과 같게 없거나 탈퇴했거나 온보딩 전인 사용자는 130000이다. 신고 규칙은 리뷰에서 고른 사용자 신고와 같다
    @Transactional
    override fun submitUserReport(
        targetPublicId: String,
        command: SubmitUserReportCommand,
    ) {
        val target = snsProfileService.requireVisible(userUseCase.findPublicProfile(targetPublicId))
        userReportUseCase.submitUserReport(command, target.userId)
    }
}
