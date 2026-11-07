package com.aechak.application.sns.withdrawal.facade

import com.aechak.application.sns.commentdeletion.usecase.SnsCommentDeletionUseCase
import com.aechak.application.sns.postdeletion.usecase.SnsPostDeletionUseCase
import com.aechak.application.sns.withdrawal.service.SnsWithdrawalService
import com.aechak.application.sns.withdrawal.usecase.SnsWithdrawalUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// shortcut: 게시물마다 태그 행과 작성자 통계 행을 잠근 뒤 팔로우 상대의 통계 행을 잠가서 그 사이 상대와 겹친 팔로우나
// 같은 태그를 단 게시가 드물게 교착한다. InnoDB가 바꾼 행이 적은 쪽을 되돌린다. 교착 로그가 보이면 태그와 통계 행을
// 게시물 전체에서 모아 이름 순서와 user_id 순서로 한 번씩 잠근다
@Service
class SnsWithdrawalFacade(
    private val snsPostDeletionUseCase: SnsPostDeletionUseCase,
    private val snsCommentDeletionUseCase: SnsCommentDeletionUseCase,
    private val snsWithdrawalService: SnsWithdrawalService,
) : SnsWithdrawalUseCase {
    // 게시가 태그 행을 통계 행보다 먼저 잠그므로 게시물을 먼저 지운다
    @Transactional(propagation = Propagation.MANDATORY)
    override fun cleanUp(userId: Long) {
        snsPostDeletionUseCase.deleteByWithdrawal(userId)
        snsWithdrawalService.removeRelations(userId)
        snsCommentDeletionUseCase.deleteByWithdrawal(userId)
    }
}
