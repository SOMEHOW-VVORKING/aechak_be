package com.aechak.application.user.withdrawal.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.social.repository.SocialIdentityRepository
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class WithdrawalService(
    private val userRepository: UserRepository,
    private val socialIdentityRepository: SocialIdentityRepository,
) {
    /**
     * @return 스토리지에서 삭제할 프로필 이미지 키
     */
    fun withdraw(userId: Long): String? {
        val user =
            userRepository.findById(userId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        // TODO: 주문과 정산 도메인이 생기면 진행 중 주문과 미정산을 확인해 WITHDRAWAL_BLOCKED로 막는다
        val profileImageKey = user.profileImageKey
        user.withdraw()
        socialIdentityRepository.findByUserId(userId)?.clearEmailAndRefreshToken()
        // TODO: 주문, 결제, CS 도메인이 생기면 실제 기록이 있는 유형만 골라 retention_records에 남긴다
        return profileImageKey
    }
}
