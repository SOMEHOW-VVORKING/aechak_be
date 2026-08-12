package com.aechak.application.seller.usecase.result;

import java.util.List;

/** 어드민 목록 페이지 — items + 필터 기준 전체 행 수. */
public record AdminApplicationPageResult(List<AdminApplicationSummaryResult> items, long totalCount) {}
