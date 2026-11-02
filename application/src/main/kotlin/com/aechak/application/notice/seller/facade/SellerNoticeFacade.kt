package com.aechak.application.notice.seller.facade

import com.aechak.application.notice.read.usecase.NoticeReadUseCase
import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.notice.read.usecase.result.NoticeDetailResult
import com.aechak.application.notice.read.usecase.result.NoticeSummaryResult
import com.aechak.application.notice.seller.usecase.SellerNoticeUseCase
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.notice.notice.enums.NoticeAudience
import org.springframework.stereotype.Service

/**
 * SellerNoticeUseCase의 유일한 구현체.
 *
 * 트랜잭션 경계를 여기에 두지 않는다 — 자격 조회와 공지 조회가 각자의 읽기 전용 경계를 갖고,
 * 둘을 한 경계로 묶어 얻는 일관성이 없다(자격이 그 사이에 바뀌어도 공지 내용은 같다).
 *
 * 자격 게이트는 "셀러로 등록된 적이 있는가"까지만 본다 — 정지된 셀러도 정책·점검 공지는 읽어야 한다.
 * 왜 정지됐고 무엇이 바뀌는지 알 길을 막으면 셀러가 대응할 수 없다.
 */
@Service
class SellerNoticeFacade(
    private val sellerUseCase: SellerUseCase,
    private val noticeReadUseCase: NoticeReadUseCase,
) : SellerNoticeUseCase {
    override fun list(
        userId: Long,
        query: NoticeListQuery,
    ): CursorPageResult<NoticeSummaryResult> {
        sellerUseCase.requireSellerStatus(userId)
        return noticeReadUseCase.list(NoticeAudience.SELLER, query)
    }

    override fun detail(
        userId: Long,
        publicId: String,
    ): NoticeDetailResult {
        sellerUseCase.requireSellerStatus(userId)
        return noticeReadUseCase.detail(NoticeAudience.SELLER, publicId)
    }
}
