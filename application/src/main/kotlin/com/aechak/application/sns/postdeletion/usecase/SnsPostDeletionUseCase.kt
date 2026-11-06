package com.aechak.application.sns.postdeletion.usecase

interface SnsPostDeletionUseCase {
    // 작성자가 자기 게시물을 지운다. 이미 지운 게시물이어도 성공이다
    fun deleteByAuthor(
        userId: Long,
        publicId: String,
    )

    // 운영자가 신고된 게시물을 지운다. 신고 처리 트랜잭션 안에서 부르고 미디어 파일은 그 트랜잭션이 커밋된 뒤에 지운다
    fun deleteByOperator(publicId: String)

    // 탈퇴하는 사용자의 게시된 게시물을 모두 지운다. 탈퇴 트랜잭션 안에서 부르고 미디어 파일은 그 트랜잭션이 커밋된 뒤에 지운다
    fun deleteByWithdrawal(authorId: Long)
}
