package com.aechak.admin.inquiry.response;

import com.aechak.application.inquiry.usecase.result.AdminInquiryAuthorResult;

/** 탈퇴 계정은 nickname이 익명 표기로 내려오고 withdrawn이 true다. */
public record AdminInquiryAuthorResponse(long userId, String nickname, boolean withdrawn) {

    public static AdminInquiryAuthorResponse from(AdminInquiryAuthorResult result) {
        return new AdminInquiryAuthorResponse(result.userId(), result.nickname(), result.withdrawn());
    }
}
