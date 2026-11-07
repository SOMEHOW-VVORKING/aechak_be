package com.aechak.admin.product.request;

import com.aechak.domain.product.product.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 판매중지 요청.
 *
 * <p>사유를 필수로 받는다 — 셀러의 판매 상태를 운영자가 잠그는 조치라, 근거 없는 행이 남으면
 * 분쟁에서 설명할 수 없다(검수 반려도 같은 이유로 사유가 필수다).
 */
public record SuspendProductSaleRequest(@NotBlank @Size(max = Product.ADMIN_SALE_BLOCK_REASON_MAX) String reason) {}
