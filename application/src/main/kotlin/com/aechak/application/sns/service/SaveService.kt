package com.aechak.application.sns.service

import com.aechak.domain.sns.save.repository.SaveRepository
import org.springframework.stereotype.Service

/** 저장 수가 없어 차단과 겹쳐도 지킬 불변식이 없으므로 통계 행을 잠그지 않는다 */
@Service
class SaveService(
    private val saveRepository: SaveRepository,
) {
    fun save(
        userId: Long,
        postId: Long,
    ) = saveRepository.insertIfAbsent(userId, postId)

    fun unsave(
        userId: Long,
        postId: Long,
    ) {
        saveRepository.delete(userId, postId)
    }
}
