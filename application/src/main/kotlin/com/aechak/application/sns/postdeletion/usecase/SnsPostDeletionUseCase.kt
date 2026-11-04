package com.aechak.application.sns.postdeletion.usecase

interface SnsPostDeletionUseCase {
    // 작성자가 자기 게시물을 지운다. 이미 지운 게시물이어도 성공이다
    fun deleteByAuthor(
        userId: Long,
        publicId: String,
    )
}
