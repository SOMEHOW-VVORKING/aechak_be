package com.aechak.application.sns.commentdeletion.service

import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import org.springframework.stereotype.Service

/**
 * 댓글 삭제의 상태 전이. 작성자 삭제, 운영자 삭제, 탈퇴 정리가 사유만 바꿔 같은 전이를 쓴다.
 * 안내 문구 여부는 저장하지 않고 목록이 읽을 때 정한다. 호출자가 댓글 행을 잠근 트랜잭션 안에서 부른다.
 */
@Service
class CommentDeletionService {
    /** 이미 지운 댓글이면 아무것도 바꾸지 않고 false를 돌려준다 */
    fun delete(
        comment: Comment,
        reason: CommentDeletionReason,
    ): Boolean = comment.delete(reason)
}
