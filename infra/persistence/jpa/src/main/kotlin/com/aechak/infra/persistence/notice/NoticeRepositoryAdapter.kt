package com.aechak.infra.persistence.notice

import com.aechak.domain.notice.notice.Notice
import com.aechak.domain.notice.notice.repository.NoticeRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface NoticeJpaRepository : JpaRepository<Notice, Long> {
    fun findByPublicId(publicId: String): Notice?

    // 쓰기 경로 전용. 단일 테이블 조회로 둬서 공지 행만 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notice n where n.publicId = :publicId")
    fun findForUpdateByPublicId(
        @Param("publicId") publicId: String,
    ): Notice?
}

@Repository
class NoticeRepositoryAdapter(
    private val jpaRepository: NoticeJpaRepository,
) : NoticeRepository {
    override fun save(notice: Notice): Notice = jpaRepository.save(notice)

    override fun findByPublicId(publicId: String): Notice? = jpaRepository.findByPublicId(publicId)

    override fun findByPublicIdForUpdate(publicId: String): Notice? = jpaRepository.findForUpdateByPublicId(publicId)
}
