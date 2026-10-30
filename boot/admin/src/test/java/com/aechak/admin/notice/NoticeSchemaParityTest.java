package com.aechak.admin.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.aechak.domain.notice.notice.Notice;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * [단위] 공지 테이블의 마이그레이션과 엔티티 선언이 어긋나지 않는지 본다.
 *
 * <p>DB를 띄워 보지 않는다. 통합 테스트는 {@code ddl-auto=create}로 엔티티에서 테이블을 다시
 * 만들기 때문에, 거기서 information_schema를 읽어 엔티티와 비교하면 자기 자신을 대조하는
 * 항진명제가 된다. 그래서 마이그레이션 <b>파일</b>을 읽어 대조한다.
 *
 * <p>어긋나면 조용히 갈린다 — 칼럼을 엔티티에만 더하면 전 수트가 녹색인데 운영 부팅에서
 * "Unknown column"이 나고, 인덱스를 한쪽에만 두면 공지 목록이 한 환경에서만 파일소트가 된다.
 */
class NoticeSchemaParityTest {

    private static final String MIGRATION = "V64__create_notices.sql";
    private static final Pattern INDEX_NAME = Pattern.compile("\\bINDEX\\s+(idx_\\w+)");

    /** 엔티티가 든 칼럼 — 하나라도 마이그레이션에 없으면 운영에서만 깨진다 */
    private static final List<String> COLUMNS = List.of(
            "public_id",
            "title",
            "content",
            "category",
            "audience",
            "status",
            "pinned",
            "published_at",
            "internal_memo",
            "created_by_admin_id",
            "last_modified_by_admin_id",
            "created_at",
            "updated_at");

    @Test
    void 마이그레이션이_만드는_인덱스를_엔티티도_같은_이름으로_선언한다() {
        Set<String> inMigration = new HashSet<>();
        Matcher matcher = INDEX_NAME.matcher(migrationText());
        while (matcher.find()) {
            inMigration.add(matcher.group(1));
        }
        Set<String> declared = Arrays.stream(
                        Notice.class.getAnnotation(Table.class).indexes())
                .map(Index::name)
                .collect(Collectors.toSet());

        assertThat(inMigration).as("마이그레이션에서 인덱스를 읽지 못했다. 파일 이름이나 구문이 바뀌었다").isNotEmpty();
        assertThat(declared).as("마이그레이션과 엔티티의 인덱스가 어긋나면 환경마다 조회 계획이 갈린다").isEqualTo(inMigration);
    }

    @Test
    void 엔티티의_칼럼을_마이그레이션도_전부_만든다() {
        String text = migrationText().toLowerCase();

        assertThat(COLUMNS).allSatisfy(column -> assertThat(text).contains(column));
    }

    /** 외부 노출 식별자는 유일해야 한다 — 겹치면 다른 공지가 같은 경로로 열린다 */
    @Test
    void 마이그레이션은_외부_식별자에_유니크_키를_둔다() {
        String text = migrationText();

        assertThat(text).contains("UNIQUE KEY uk_notices_public_id");
        assertThat(Arrays.stream(Notice.class.getAnnotation(Table.class).uniqueConstraints())
                        .map(UniqueConstraint::name)
                        .toList())
                .containsExactly("uk_notices_public_id");
    }

    /**
     * 자르기로 한 범위가 스키마에 몰래 들어오지 않는지 본다 — 발송(푸시·메일)과 예약 게시는
     * 전이시킬 배치도 조립된 어댑터도 없어서, 컬럼만 생기면 영원히 안 움직이는 값이 계약에 남는다.
     */
    @Test
    void 마이그레이션은_발송과_예약_게시_컬럼을_두지_않는다() {
        String text = migrationText().toLowerCase();

        assertThat(text).doesNotContain("push");
        assertThat(text).doesNotContain("scheduled_at");
        assertThat(text).doesNotContain("reserved_at");
        assertThat(text).doesNotContain("sent_at");
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
