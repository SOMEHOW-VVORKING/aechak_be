package com.aechak.application.review.admin.support;

import com.aechak.application.review.admin.usecase.query.AdminReviewSearchQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.common.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 목록 커서. 필터 지문을 함께 실어 조건이 바뀐 채 이전 커서를 다시 보내면 거절한다 —
 * 지문을 싣지 않으면 "별점 1점"에서 받은 커서를 "전체"에 끼워 넣어 앞쪽 페이지가 조용히 건너뛰어진다.
 * 지문만 비교하므로 커서에 필터 원문(검색어)이 실리지 않는다.
 */
public final class AdminReviewCursorCodec {

    private static final String TAG = "arv";
    private static final String SEPARATOR = ":";
    private static final int FINGERPRINT_LENGTH = 16;

    private AdminReviewCursorCodec() {}

    public static String encode(AdminReviewSearchQuery query, long lastReviewId) {
        if (lastReviewId <= 0) {
            throw new IllegalArgumentException("lastReviewId must be positive.");
        }
        String payload = String.join(SEPARATOR, TAG, fingerprint(query), String.valueOf(lastReviewId));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서가 깨졌거나 조건이 달라졌으면 400(90002). */
    public static long decodeLastReviewId(String cursor, AdminReviewSearchQuery query) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, e, null);
        }
        String[] fields = decoded.split(SEPARATOR, -1);
        if (fields.length != 3 || !TAG.equals(fields[0]) || !fingerprint(query).equals(fields[1])) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, null, null);
        }
        long lastReviewId;
        try {
            lastReviewId = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, e, null);
        }
        if (lastReviewId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, null, null);
        }
        return lastReviewId;
    }

    private static String fingerprint(AdminReviewSearchQuery query) {
        String canonical = String.join(
                "\u001f",
                nullSafe(query.productPublicId()),
                query.rating() == null ? "" : query.rating().toString(),
                query.status() == null ? "" : query.status().name(),
                String.valueOf(query.reportedOnly()),
                nullSafe(query.keyword()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
