package com.aechak.admin.member.response;

import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;

/**
 * 회원 목록·상세의 한 회원.
 *
 * <p>status는 지금 실제로 적용되는 상태, storedStatus는 저장값이다. 임시 정지 기간이 지나면 둘이 갈리는데
 * 운영자가 "이미 풀렸다"와 "정지를 걸었던 이력이 있다"를 함께 봐야 하므로 둘 다 내려준다.
 * nickname은 온보딩 전 계정이거나 탈퇴로 프로필이 지워진 계정이면 null이다.
 */
public record MemberSummaryResponse(
        long userId,
        String publicId,
        String nickname,
        UserStatus status,
        UserStatus storedStatus,
        LocalDateTime sanctionUntil,
        int warningCount,
        long reportCount,
        LocalDateTime joinedAt) {

    public static MemberSummaryResponse from(MemberSummaryResult result) {
        return new MemberSummaryResponse(
                result.getUserId(),
                result.getPublicId(),
                result.getNickname(),
                result.getStatus(),
                result.getStoredStatus(),
                result.getSanctionUntil(),
                result.getWarningCount(),
                result.getReportCount(),
                result.getJoinedAt());
    }
}
