package com.aechak.admin.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.aechak.admin.report.response.AdminReportSummaryResponse;
import com.aechak.admin.report.response.ReportHistoryResponse;
import com.aechak.domain.product.report.enums.ProductReportReason;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.user.report.enums.ReportReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 응답의 {@code reasonCode} 허용값이 세 신고 사유 enum의 합집합과 같은지 못박는다.
 *
 * <p>세 종류(상품·리뷰·사용자)의 사유 어휘가 서로 달라 공통 응답 필드는 문자열이다. 문자열 필드는
 * {@code @Schema(allowableValues)} 말고 허용값을 생성 문서에 실을 자리가 없고(docs/30-boot.md 3-2),
 * 그 목록은 손으로 적은 사본이라 원본 enum이 늘면 조용히 낡는다. 어드민에는 전수 계약 테스트가
 * 없으므로 이 축만 따로 지킨다 — 사유를 더하면 이 테스트가 먼저 깨져 목록 갱신을 강제한다.
 */
class ReportReasonCodeSchemaTest {

    /** 신고 접수 쪽 사유 어휘의 합집합 — 응답 문서가 약속해야 하는 전부. */
    private static final Set<String> ALL_REPORT_REASONS = new TreeSet<>(Stream.of(
                    Arrays.stream(ProductReportReason.values()).map(Enum::name),
                    Arrays.stream(ReviewReportReason.values()).map(Enum::name),
                    Arrays.stream(ReportReasonCode.values()).map(Enum::name))
            .flatMap(stream -> stream)
            .toList());

    @Test
    void 목록_응답의_신고_사유_허용값은_세_어휘의_합집합이다() {
        assertThat(allowableValuesOf(AdminReportSummaryResponse.class, "reasonCode"))
                .containsExactlyElementsOf(ALL_REPORT_REASONS);
    }

    @Test
    void 이력_응답의_신고_사유_허용값은_세_어휘의_합집합이다() {
        assertThat(allowableValuesOf(ReportHistoryResponse.class, "reasonCode"))
                .containsExactlyElementsOf(ALL_REPORT_REASONS);
    }

    @Test
    void 합집합이_비어_있지_않다() {
        // 위 두 단언이 "둘 다 빈 목록"으로 통과하지 못하게 막는다
        assertThat(ALL_REPORT_REASONS).hasSizeGreaterThanOrEqualTo(5);
    }

    /**
     * 레코드의 {@code @Schema} 허용값. 접근자와 필드 양쪽에서 읽어 같은 값인지까지 본다.
     *
     * <p>{@code RecordComponent.getAnnotation}으로는 읽히지 않는다 — {@code @Schema}의
     * {@code @Target}에 {@code RECORD_COMPONENT}가 없어 애노테이션이 컴포넌트에 남지 않고
     * 필드와 접근자로만 전파된다. springdoc(Jackson)이 보는 자리가 그 둘이라 문서에는 제대로 실린다.
     */
    private static List<String> allowableValuesOf(Class<?> responseType, String componentName) {
        assertThat(Arrays.stream(responseType.getRecordComponents()).map(RecordComponent::getName))
                .as("%s 에 %s 컴포넌트가 있어야 한다", responseType.getSimpleName(), componentName)
                .contains(componentName);

        Schema onAccessor = annotationOf(responseType, componentName, false);
        Schema onField = annotationOf(responseType, componentName, true);
        assertThat(Arrays.asList(onField.allowableValues()))
                .as("필드와 접근자의 허용값이 갈리면 문서와 응답이 벌어진다")
                .containsExactlyElementsOf(Arrays.asList(onAccessor.allowableValues()));
        return new TreeSet<>(Arrays.asList(onAccessor.allowableValues()))
                .stream().toList();
    }

    /** 접근자(또는 필드)에 달린 {@code @Schema}. 없으면 허용값이 문서에 안 실리므로 실패한다. */
    private static Schema annotationOf(Class<?> responseType, String name, boolean field) {
        Schema schema;
        try {
            schema = field
                    ? responseType.getDeclaredField(name).getAnnotation(Schema.class)
                    : responseType.getMethod(name).getAnnotation(Schema.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(responseType.getSimpleName() + "." + name + " 을 찾을 수 없다", e);
        }
        assertThat(schema)
                .as(
                        "%s.%s(%s) 에 @Schema 가 없다 — 문자열 필드는 허용값이 문서에 실리지 않는다",
                        responseType.getSimpleName(), name, field ? "필드" : "접근자")
                .isNotNull();
        return schema;
    }
}
