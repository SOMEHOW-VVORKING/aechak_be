package com.aechak.application.sns.commentdeletion.usecase

interface SnsCommentDeletionUseCase {
    // 운영자가 신고된 댓글을 지운다. 신고 처리 트랜잭션 안에서 부르고 이미 지운 댓글이면 그대로 둔다
    fun deleteByOperator(publicId: String)
}
