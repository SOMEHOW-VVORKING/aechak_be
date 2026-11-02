package com.aechak.application.notice.admin.support;

import com.aechak.application.notice.admin.port.AdminNoticeAnchor;
import com.aechak.common.error.BusinessException;
import com.aechak.common.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 운영자용 공지 목록 커서 코덱 — 정렬 키(작성 시각, id)를 그대로 싣는다.
 *
 * <p>필터 조건은 커서에 넣지 않는다: 운영자가 필터를 바꿔도 같은 앵커에서 이어 읽히는 쪽이
 * 자연스럽고, 필터별 커서를 강제하면 "조건 바꾸면 커서 무효" 규칙을 프론트가 따라와야 한다.
 */
public final class AdminNoticeCursorCodec {

    private static final String TAG = "an";
    private static final String SEPARATOR = ":";
    private static final int FIELD_COUNT = 3;

    // DB DATETIME(6) 정밀도에 맞춰 마이크로초로 보존한다 — 밀리초로 깎으면 동시각 행을 건너뛴다.
    private static final LocalDateTime CURSOR_EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    // MySQL DATETIME의 상한. 양수 검사만 하면 Long.MAX_VALUE 마이크로초도 통과하는데
    // 그 시각을 조회로 흘려보내면 MySQL이 ERROR 1525로 끊어 400이 아니라 500이 된다.
    private static final LocalDateTime CURSOR_MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000);
    private static final long MAX_MICROS = ChronoUnit.MICROS.between(CURSOR_EPOCH, CURSOR_MAX);

    private AdminNoticeCursorCodec() {}

    public static String encode(LocalDateTime lastCreatedAt, long lastNoticeId) {
        if (lastNoticeId <= 0) {
            throw new IllegalArgumentException("lastNoticeId는 양수여야 합니다.");
        }
        long micros = ChronoUnit.MICROS.between(CURSOR_EPOCH, lastCreatedAt.truncatedTo(ChronoUnit.MICROS));
        if (micros <= 0) {
            throw new IllegalArgumentException("lastCreatedAt은 커서 기준 시각 이후여야 합니다.");
        }
        String payload = String.join(SEPARATOR, TAG, Long.toString(micros), Long.toString(lastNoticeId));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static AdminNoticeAnchor decode(String encodedCursor) {
        String[] fields;
        try {
            fields = new String(Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8)
                    .split(SEPARATOR);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, e, null);
        }
        if (fields.length != FIELD_COUNT || !TAG.equals(fields[0])) {
            throw invalidCursor();
        }
        return new AdminNoticeAnchor(
                CURSOR_EPOCH.plus(micros(fields[1]), ChronoUnit.MICROS), positiveLong(fields[2]));
    }

    /** 시각 필드는 양수인 것으로 끝나지 않는다 — DB가 담을 수 있는 범위까지 좁혀야 조회로 새지 않는다. */
    private static long micros(String field) {
        long value = positiveLong(field);
        if (value > MAX_MICROS) {
            throw invalidCursor();
        }
        return value;
    }

    private static long positiveLong(String field) {
        try {
            long value = Long.parseLong(field);
            if (value <= 0) {
                throw invalidCursor();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR, e, null);
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.INVALID_CURSOR, null, null);
    }
}
