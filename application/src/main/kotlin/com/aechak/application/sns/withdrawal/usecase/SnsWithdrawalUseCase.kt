package com.aechak.application.sns.withdrawal.usecase

interface SnsWithdrawalUseCase {
    // 탈퇴하는 사용자의 SNS 게시물, 관계, 반응, 댓글을 정리한다. 탈퇴 트랜잭션 안에서 부른다
    fun cleanUp(userId: Long)
}
