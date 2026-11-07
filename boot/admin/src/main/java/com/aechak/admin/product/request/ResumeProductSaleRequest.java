package com.aechak.admin.product.request;

import com.aechak.domain.product.product.Product;
import jakarta.validation.constraints.Size;

/** 판매 재개 요청 — 사유는 선택이다. 적으면 감사 기록에만 남고 상품에는 남지 않는다(막음 표시가 지워진다). */
public record ResumeProductSaleRequest(@Size(max = Product.ADMIN_SALE_BLOCK_REASON_MAX) String reason) {}
