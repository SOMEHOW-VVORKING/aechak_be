package com.aechak.admin.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.aechak.admin.report.response.AdminReportSummaryResponse;
import com.aechak.admin.report.response.ReportHistoryResponse;
import com.aechak.domain.product.report.enums.ProductReportReason;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.user.report.enums.ReportReasonCode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 생성 문서에 실리는 {@code reasonCode} 허용값이 세 신고 사유 enum의 합집합과 같은지 못박는다.
 *
 * <p>세 종류(상품·리뷰·사용자)의 사유 어휘가 서로 달라 공통 응답 필드는 문자열이다. 문자열 필드는
 * {@code @Schema(allowableValues)} 말고 허용값을 실을 자리가 없고, 그 목록은 손으로 적은 사본이라
 * 원본 enum이 늘면 조용히 낡는다. 어드민에는 전수 계약 테스트가 없으므로 이 축만 따로 지킨다.
 *
 * <p>애노테이션을 리플렉션으로 읽지 않고 springdoc이 쓰는 변환기(swagger-core)를 돌려 실제로
 * 생성되는 스키마를 본다 — 애노테이션이 달려 있어도 문서에 안 실리는 경우를 그래야 잡는다.
 * ({@code @Schema}의 {@code @Target}에 {@code RECORD_COMPONENT}가 없어 애노테이션은 레코드
 * 컴포넌트에 남지 않고 필드·접근자로만 전파된다 — 컴포넌트에서 읽으면 늘 비어 있다.)
 */
class ReportReasonCodeSchemaTest {

    /** 신고 접수 쪽 사유 어휘의 합집합 — 문서가 약속해야 하는 전부. */
    private static final Set<String> ALL_REPORT_REASONS = new TreeSet<>(Stream.of(
                    Arrays.stream(ProductReportReason.values()).map(Enum::name),
                    Arrays.stream(ReviewReportReason.values()).map(Enum::name),
                    Arrays.stream(ReportReasonCode.values()).map(Enum::name))
            .flatMap(stream -> stream)
            .toList());

    @Test
    void 목록_응답의_신고_사유_허용값은_세_어휘의_합집합이다() {
        assertThat(documentedReasonCodeEnum(AdminReportSummaryResponse.class))
                .containsExactlyElementsOf(ALL_REPORT_REASONS);
    }

    @Test
    void 이력_응답의_신고_사유_허용값은_세_어휘의_합집합이다() {
        assertThat(documentedReasonCodeEnum(ReportHistoryResponse.class)).containsExactlyElementsOf(ALL_REPORT_REASONS);
    }

    @Test
    void 합집합이_비어_있지_않다() {
        // 위 두 단언이 "둘 다 빈 목록"으로 통과하지 못하게 막는다
        assertThat(ALL_REPORT_REASONS).hasSizeGreaterThanOrEqualTo(5);
    }

    /** 생성 스키마의 {@code reasonCode} 허용값. 허용값이 없으면 자유 문자열로 실린 것이므로 실패한다. */
    private static List<String> documentedReasonCodeEnum(Class<?> responseType) {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(responseType);
        Schema<?> schema = schemas.get(responseType.getSimpleName());
        assertThat(schema).as("%s 스키마가 생성되지 않았다", responseType.getSimpleName()).isNotNull();

        Schema<?> reasonCode = (Schema<?>) schema.getProperties().get("reasonCode");
        assertThat(reasonCode)
                .as("%s 에 reasonCode 프로퍼티가 없다", responseType.getSimpleName())
                .isNotNull();
        assertThat(reasonCode.getEnum())
                .as("%s.reasonCode 가 허용값 없는 자유 문자열로 실렸다 — 생성 클라이언트가 사유 계약을 얻지 못한다", responseType.getSimpleName())
                .isNotNull()
                .isNotEmpty();

        return reasonCode.getEnum().stream().map(String::valueOf).sorted().toList();
    }
}
