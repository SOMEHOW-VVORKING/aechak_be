package com.aechak.application.review.admin.facade;

import com.aechak.application.file.usecase.FileUseCase;
import com.aechak.application.review.admin.port.AdminReviewListCondition;
import com.aechak.application.review.admin.port.AdminReviewQueryPort;
import com.aechak.application.review.admin.port.view.AdminReviewCountView;
import com.aechak.application.review.admin.port.view.AdminReviewView;
import com.aechak.application.review.admin.support.AdminReviewCursorCodec;
import com.aechak.application.review.admin.usecase.AdminReviewUseCase;
import com.aechak.application.review.admin.usecase.command.BlindReviewCommand;
import com.aechak.application.review.admin.usecase.command.DeleteReviewCommand;
import com.aechak.application.review.admin.usecase.command.UnblindReviewCommand;
import com.aechak.application.review.admin.usecase.query.AdminReviewSearchQuery;
import com.aechak.application.review.admin.usecase.result.AdminReviewDetailResult;
import com.aechak.application.review.admin.usecase.result.AdminReviewImageResult;
import com.aechak.application.review.admin.usecase.result.AdminReviewReportResult;
import com.aechak.application.review.admin.usecase.result.AdminReviewSummaryResult;
import com.aechak.application.review.admin.service.AdminReviewService;
import com.aechak.application.review.port.ReviewListQueryPort;
import com.aechak.application.review.usecase.ReviewRatingUseCase;
import com.aechak.application.support.CursorPageResult;
import com.aechak.application.support.CursorPageSize;
import com.aechak.application.user.user.usecase.UserUseCase;
import com.aechak.application.user.user.usecase.result.UserAuthorResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.review.error.ReviewErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminReviewUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>비노출 전이마다 상품 평점을 같은 트랜잭션에서 다시 센다. 조회 시점 집계(rating buckets)는
 * 이미 블라인드를 제외하지만 상품 목록·상세가 읽는 값은 product_stats에 비정규화돼 있고, 그 값을
 * 갱신하는 경로는 리뷰 작성·삭제 카프카 이벤트뿐이다 — 어드민은 카프카를 조립하지 않으므로
 * 재집계를 이벤트에 맡기면 블라인드해도 평점이 그대로 남는다. 운영자 수동 처리라 호출 빈도가 낮아
 * 동기 재집계의 비용이 문제가 되지 않는다.
 *
 * <p>[남은 한계] 재집계는 버킷을 읽어 절대값으로 upsert한다. 같은 상품의 서로 다른 리뷰를 둘이
 * 동시에 처리하면 각자 상대의 미커밋 상태를 못 보고 같은 값을 계산해, 나중에 쓰는 쪽이 틀린
 * 절대값을 남길 수 있다(카프카 소비자의 재집계도 같은 결). 잠금만 더해서는 안 고쳐진다 —
 * 기본 격리(REPEATABLE READ)에서 버킷 조회는 트랜잭션 시작 시점 스냅샷을 읽으므로 product_stats
 * 행을 잠가도 낡은 값을 센다. 고치려면 재집계 경로 전체를 READ COMMITTED + 행 잠금으로 바꾸거나
 * 커밋 후 직렬화된 트랜잭션으로 옮겨야 하고, 그건 api 소비자까지 함께 바뀌는 프로젝션 설계 결정이다.
 * 어긋난 값은 그 상품에 리뷰 작성·삭제가 한 번 더 일어나면 소비자 재집계가 전건 재계산으로 바로잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewFacade implements AdminReviewUseCase {

    private final AdminReviewQueryPort adminReviewQueryPort;
    private final AdminReviewService adminReviewService;
    private final ReviewListQueryPort reviewListQueryPort;
    private final ReviewRatingUseCase reviewRatingUseCase;
    private final UserUseCase userUseCase;
    private final FileUseCase fileUseCase;

    @Transactional(readOnly = true)
    @Override
    public CursorPageResult<AdminReviewSummaryResult> list(AdminReviewSearchQuery rawQuery) {
        // 커서 지문과 실제 필터가 같은 값을 보게 검색어를 먼저 다듬는다 — 다듬기를 조건에만 걸면
        // 공백 차이만 있는 같은 검색이 지문 불일치로 거절된다
        AdminReviewSearchQuery query = withNormalizedKeyword(rawQuery);
        Long lastReviewId =
                query.cursor() == null ? null : AdminReviewCursorCodec.decodeLastReviewId(query.cursor(), query);
        AdminReviewListCondition condition = new AdminReviewListCondition(
                query.productPublicId(),
                query.rating(),
                query.status(),
                query.reportedOnly(),
                query.keyword(),
                lastReviewId,
                CursorPageSize.INSTANCE.fetchLimit(query.size()));

        List<AdminReviewView> fetched = adminReviewQueryPort.findPage(condition);
        List<Long> reviewIds = fetched.stream().map(AdminReviewView::reviewId).toList();
        Map<Long, Long> photoCounts = toCountMap(adminReviewQueryPort.countPhotosByReviewIds(reviewIds));
        Map<Long, Long> reportCounts = toCountMap(adminReviewQueryPort.countReportsByReviewIds(reviewIds));
        Map<Long, UserAuthorResult> authors =
                userUseCase.getAuthors(fetched.stream().map(AdminReviewView::authorUserId).toList());

        // 첫 페이지에만 전체 건수를 센다 — 커서로 넘어간 뒤 같은 COUNT를 매번 다시 돌리지 않는다
        Long totalCount = query.cursor() == null ? adminReviewQueryPort.count(condition) : null;

        return CursorPageResult.Companion.of(
                fetched.stream()
                        .map(view -> toSummary(view, authors, photoCounts, reportCounts))
                        .toList(),
                query.size(),
                totalCount,
                last -> AdminReviewCursorCodec.encode(query, last.reviewId()));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminReviewDetailResult detail(long reviewId) {
        AdminReviewView view = adminReviewQueryPort.findById(reviewId);
        if (view == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND, null, null);
        }
        UserAuthorResult author = userUseCase.getAuthors(List.of(view.authorUserId())).get(view.authorUserId());
        // 어댑터가 sortOrder 오름차순으로 돌려주므로 여기서 다시 정렬하지 않는다
        List<AdminReviewImageResult> images = reviewListQueryPort.findImagesByReviewIds(List.of(reviewId)).stream()
                .map(image -> new AdminReviewImageResult(
                        fileUseCase.resolveMediaUrl(image.getStorageKey()), image.getSortOrder()))
                .toList();
        List<AdminReviewReportResult> reports = adminReviewQueryPort.findReportsByReviewId(reviewId).stream()
                .map(AdminReviewReportResult::from)
                .toList();

        return AdminReviewDetailResult.from(view, nickname(author), withdrawn(author), images, reports);
    }

    @Transactional
    @Override
    public void blind(BlindReviewCommand command) {
        long productId =
                adminReviewService.blind(command.adminId(), command.reviewId(), command.reasonCode(), command.reasonText());
        reviewRatingUseCase.recomputeProductRating(productId);
        log.info(
                "리뷰 블라인드 adminId={} reviewId={} reasonCode={}",
                command.adminId(),
                command.reviewId(),
                command.reasonCode());
    }

    @Transactional
    @Override
    public void unblind(UnblindReviewCommand command) {
        long productId = adminReviewService.unblind(command.reviewId());
        reviewRatingUseCase.recomputeProductRating(productId);
        log.info("리뷰 블라인드 해제 adminId={} reviewId={}", command.adminId(), command.reviewId());
    }

    @Transactional
    @Override
    public void delete(DeleteReviewCommand command) {
        long productId = adminReviewService.delete(
                command.adminId(), command.reviewId(), command.reasonCode(), command.reasonText());
        reviewRatingUseCase.recomputeProductRating(productId);
        log.info(
                "리뷰 삭제 adminId={} reviewId={} reasonCode={}",
                command.adminId(),
                command.reviewId(),
                command.reasonCode());
    }

    private AdminReviewSummaryResult toSummary(
            AdminReviewView view,
            Map<Long, UserAuthorResult> authors,
            Map<Long, Long> photoCounts,
            Map<Long, Long> reportCounts) {
        UserAuthorResult author = authors.get(view.authorUserId());
        return AdminReviewSummaryResult.from(
                view,
                nickname(author),
                withdrawn(author),
                photoCounts.getOrDefault(view.reviewId(), 0L),
                reportCounts.getOrDefault(view.reviewId(), 0L));
    }

    private static Map<Long, Long> toCountMap(List<AdminReviewCountView> counts) {
        return counts.stream()
                .collect(Collectors.toMap(AdminReviewCountView::reviewId, AdminReviewCountView::count));
    }

    /** 공백만 들어온 검색어는 필터가 없는 것으로 본다 — LIKE '%%'는 전건 스캔이다. */
    private static AdminReviewSearchQuery withNormalizedKeyword(AdminReviewSearchQuery query) {
        String keyword = query.keyword() == null ? null : query.keyword().strip();
        if (keyword != null && keyword.isEmpty()) {
            keyword = null;
        }
        return new AdminReviewSearchQuery(
                query.productPublicId(),
                query.rating(),
                query.status(),
                query.reportedOnly(),
                keyword,
                query.cursor(),
                query.size());
    }

    private static String nickname(UserAuthorResult author) {
        return author == null ? null : author.getNickname();
    }

    private static boolean withdrawn(UserAuthorResult author) {
        return author != null && author.getWithdrawn();
    }
}
