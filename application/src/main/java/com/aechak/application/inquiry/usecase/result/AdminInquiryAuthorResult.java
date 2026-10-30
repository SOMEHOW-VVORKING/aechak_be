package com.aechak.application.inquiry.usecase.result;

import com.aechak.application.user.user.usecase.result.UserAuthorResult;

/**
 * 문의 작성자 표시 정보. user BC의 UserAuthorResult를 그대로 노출하지 않고 이 BC가 쓰는 만큼만 옮겨 담는다.
 * 탈퇴 계정은 닉네임이 익명 표기로 바뀐 상태로 넘어오고, withdrawn으로 그 사실이 구분된다.
 */
public record AdminInquiryAuthorResult(long userId, String nickname, boolean withdrawn) {

    public static AdminInquiryAuthorResult from(UserAuthorResult author) {
        return new AdminInquiryAuthorResult(author.getUserId(), author.getNickname(), author.getWithdrawn());
    }
}
