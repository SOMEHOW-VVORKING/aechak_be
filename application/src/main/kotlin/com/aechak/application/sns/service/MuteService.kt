package com.aechak.application.sns.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.mute.repository.MuteRepository
import org.springframework.stereotype.Service

@Service
class MuteService(
    private val muteRepository: MuteRepository,
) {
    /** 팔로우와 수를 바꾸지 않아 통계 행을 잠그지 않는다 */
    fun mute(
        userId: Long,
        targetUserId: Long,
    ) {
        if (userId == targetUserId) throw BusinessException(SnsErrorCode.CANNOT_MUTE_SELF)
        muteRepository.insertIfAbsent(userId, targetUserId)
    }

    fun unmute(
        userId: Long,
        targetUserId: Long,
    ) {
        muteRepository.delete(userId, targetUserId)
    }
}
