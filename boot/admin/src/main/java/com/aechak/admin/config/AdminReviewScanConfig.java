package com.aechak.admin.config;

import com.aechak.application.review.facade.ReviewRatingFacade;
import com.aechak.application.review.service.ReviewQueryService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 어드민 리뷰 관리 스캔.
 *
 * <p>어드민 전용 유스케이스는 하위 패키지(review.admin)에 모여 있어 패키지 단위로 그대로 스캔한다.
 * 같은 BC의 구매자측 조립(작성·조회 파사드, 금칙어 판정)은 카프카 배선·금칙어 스캐너 포트를 요구해
 * 어드민이 들 수 없으므로 애초에 스캔 범위에 들어오지 않는다.
 *
 * <p>평점 재집계만 구매자측과 공유한다 — 집계 규칙을 두 벌로 두면 블라인드가 반영한 평점과 리뷰
 * 작성이 반영한 평점이 다른 기준으로 갈린다. 그래서 ReviewRatingFacade와 그 의존(ReviewQueryService)
 * 두 개만 이름으로 집어 온다. 패키지 단위로 열면 같은 패키지의 ReviewCommandFacade(메시지 발행 포트)가
 * 딸려와 부팅이 깨진다.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = {
            "com.aechak.application.review.admin", // 어드민 리뷰 유스케이스·조회 포트 소비자
            "com.aechak.application.product.stats" // 평점 집계 반영(ProductStatsUseCase)
        })
@ComponentScan(
        basePackages = {"com.aechak.application.review.facade", "com.aechak.application.review.service"},
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {ReviewRatingFacade.class, ReviewQueryService.class}))
public class AdminReviewScanConfig {}
