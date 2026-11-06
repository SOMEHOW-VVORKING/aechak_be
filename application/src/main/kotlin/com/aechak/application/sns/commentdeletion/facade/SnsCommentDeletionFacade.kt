package com.aechak.application.sns.commentdeletion.facade

import com.aechak.application.sns.commentdeletion.service.CommentDeletionService
import com.aechak.application.sns.commentdeletion.usecase.SnsCommentDeletionUseCase
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** SNS 댓글 삭제 진입점. 금지어 판정을 쓰지 않아 다른 실행 모듈이 이 패키지만 스캔할 수 있다 */
@Service
class SnsCommentDeletionFacade(
    private val commentDeletionService: CommentDeletionService,
) : SnsCommentDeletionUseCase {
    // 제재가 거절되면 결정 기록과 함께 되돌아가도록 신고 처리 트랜잭션에 합류한다
    @Transactional(propagation = Propagation.MANDATORY)
    override fun deleteByOperator(publicId: String) {
        commentDeletionService.delete(commentDeletionService.lock(publicId), CommentDeletionReason.OPERATOR)
    }
}
