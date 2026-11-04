package com.aechak.domain.sns.hashtag.repository

import com.aechak.domain.sns.hashtag.Hashtag

interface HashtagRepository {
    /**
     * 정규화한 이름으로 태그별 게시글 수를 고친다. added는 +1(행이 없으면 만든다), removed는 -1(post_count > 0)이고
     * 두 목록을 합쳐 이름 오름차순으로 한 번에 처리한다. 두 목록에 함께 든 이름은 수를 바꾸지 않는다.
     */
    fun adjustCounts(
        added: Collection<String> = emptyList(),
        removed: Collection<String> = emptyList(),
    )

    fun findByName(name: String): Hashtag?

    fun findAllByNameIn(names: Collection<String>): List<Hashtag>

    fun findAllById(ids: Collection<Long>): List<Hashtag>

    /** 이름에 keyword가 글자 그대로 든 태그 중 게시글 수가 1 이상인 것을 수가 많은 순, 같으면 이름 순으로 limit개까지. 빈 keyword는 모든 태그와 맞는다 */
    fun findPopular(
        keyword: String,
        limit: Int,
    ): List<Hashtag>
}
