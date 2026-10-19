package com.aechak.admin.seller.request;

import jakarta.validation.constraints.Size;

/**
 * 반려 요청 — 사유 필수(빈 값 10102)는 도메인 검증 소관이라 여기선 형식(길이)만 본다.
 * reason이 null이어도 400(90001)이 아니라 10102로 나가도록 nullable로 받아 흘려보낸다.
 */
public record RejectApplicationRequest(@Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.") String reason) {}
