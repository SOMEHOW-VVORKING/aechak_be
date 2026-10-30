package com.aechak.admin.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * [단위] 자르기로 한 범위가 스키마·상태 어휘로 몰래 들어오지 않는지 본다.
 *
 * <p>예약 게시는 시각이 됐을 때 게시로 옮겨 줄 배치가 없고, 발송(푸시·메일)은 어드민 실행 모듈에
 * 어댑터가 조립돼 있지 않다. 컬럼이나 상태값만 생기면 영원히 안 움직이는 값이 계약에 남아
 * 프런트가 "예약됐다"·"보냈다"로 읽는다. 둘 중 하나를 넣는 PR이 이 테스트를 같이 고치게 둔다.
 *
 * <p>상태 어휘가 셋뿐이라는 것은 enum이 사는 domain 모듈의 {@code NoticeTest}가 못 박는다.
 *
 * <p>마이그레이션과 엔티티의 실제 스키마 정합은 여기서 보지 않는다 — 파일 텍스트를 훑는 검사는
 * 타입·길이·nullable·인덱스 구성을 못 본다. 그 대조는 실 MySQL에 두 스키마를 만들어 비교하는
 * {@code boot/api/src/test/.../schema/V73NoticesMigrationIntegrationTest}가 한다.
 */
class NoticeCutScopeTest {

    private static final String MIGRATION = "V73__create_notices.sql";

    @Test
    void 마이그레이션은_발송과_예약_게시_컬럼을_두지_않는다() {
        String text = migrationText().toLowerCase();

        assertThat(text).doesNotContain("push");
        assertThat(text).doesNotContain("scheduled_at");
        assertThat(text).doesNotContain("reserved_at");
        assertThat(text).doesNotContain("sent_at");
        assertThat(text).doesNotContain("read_at");
    }

    private String migrationText() {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/" + MIGRATION)) {
            assertThat(stream).as("마이그레이션을 찾지 못했다: %s", MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
