package com.aechak.application.sns.service

import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import org.springframework.stereotype.Service

@Service
class HashtagService(
    private val hashtagRepository: HashtagRepository,
) {
    /** 태그 id들의 이름을 받은 순서대로 돌려준다 */
    fun namesOf(hashtagIds: List<Long>): List<String> {
        val names = hashtagRepository.findAllById(hashtagIds).associate { it.id to it.name }
        return hashtagIds.mapNotNull(names::get)
    }
}
