package com.aechak.admin.product.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 반려 사유는 셀러 검수 화면에 그대로 보임 */
public record RejectProductInspectionRequest(
        @NotBlank(message = "반려 사유를 입력해 주세요.") @Size(max = 500, message = "반려 사유는 {max}자 이하여야 합니다.") String reason) {}
