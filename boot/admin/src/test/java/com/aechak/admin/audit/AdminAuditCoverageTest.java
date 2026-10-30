package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.webcommon.trace.TraceIdFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * [통합] <b>빠뜨림을 막는 테스트.</b> 어드민의 모든 쓰기 핸들러가 감사 기록을 한 번은 결정했는지 본다.
 *
 * <p>유스케이스마다 기록 호출을 직접 흩뿌리는 방식의 진짜 문제는 "틀리는 것"이 아니라 "빠뜨린 것이
 * 보이지 않는 것"이다 — 새 어드민 조치를 만든 사람이 감사 기록을 생각하지 않으면 그 조치는
 * 영원히 추적 밖에 남고, 아무 테스트도 깨지지 않는다. 그래서 쓰기 핸들러에
 * {@link AdminAudited}나 {@link AdminAuditExempt} 중 하나가 없으면 여기서 빌드를 끊는다.
 *
 * <p>면제를 허용하는 이유는 전부를 기록하는 것이 목적이 아니기 때문이다 — 목적은 **결정이 한 번
 * 이루어지는 것**이고, 면제에는 글로 쓴 이유가 남는다.
 */
class AdminAuditCoverageTest extends IntegrationTestBase {

    private static final String ADMIN_PACKAGE = "com.aechak.admin";
    private static final Set<RequestMethod> MUTATING =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    /** Spring Security 필터 체인의 기본 등록 순서(SecurityProperties.DEFAULT_FILTER_ORDER) */
    private static final int SECURITY_CHAIN_ORDER = -100;

    // springdoc도 같은 타입의 빈을 하나 더 등록한다 — 우리 핸들러를 든 쪽을 이름으로 집는다
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 어드민_쓰기_핸들러는_전부_감사_기록_여부를_선언한다() {
        List<String> undeclared = new ArrayList<>();

        for (HandlerMethod handlerMethod : mutatingAdminHandlers()) {
            boolean audited = handlerMethod.getMethodAnnotation(AdminAudited.class) != null;
            boolean exempt = handlerMethod.getMethodAnnotation(AdminAuditExempt.class) != null;
            if (audited == exempt) {
                undeclared.add(signature(handlerMethod) + (audited ? " (둘 다 붙었다)" : " (둘 다 없다)"));
            }
        }

        assertThat(undeclared)
                .as("어드민 쓰기 핸들러는 @AdminAudited 또는 @AdminAuditExempt 중 정확히 하나를 가져야 한다")
                .isEmpty();
    }

    /**
     * {@code targetVar} 오타는 런타임에 "대상 미해결" 로그로만 드러나고, 그때는 이미 그 조치의 행이
     * 비어 있다. 이름이 실제 경로 변수인지 여기서 본다.
     */
    @Test
    void targetVar는_그_핸들러의_실제_경로_변수를_가리킨다() {
        List<String> broken = new ArrayList<>();

        for (HandlerMethod handlerMethod : mutatingAdminHandlers()) {
            AdminAudited audited = handlerMethod.getMethodAnnotation(AdminAudited.class);
            if (audited == null || audited.targetVar().isEmpty()) {
                continue;
            }
            if (!pathVariableNames(handlerMethod).contains(audited.targetVar())) {
                broken.add(signature(handlerMethod) + " targetVar=" + audited.targetVar() + " 경로변수="
                        + pathVariableNames(handlerMethod));
            }
        }

        assertThat(broken).as("targetVar가 그 핸들러의 @PathVariable 이름과 맞지 않는다").isEmpty();
    }

    /** 감사 로그 자체에는 쓰기 핸들러가 없어야 한다 — 고칠 수 있는 기록은 증거가 되지 못한다 */
    @Test
    void 감사_로그_컨트롤러에는_쓰기_핸들러가_없다() {
        List<String> writeHandlers = mutatingAdminHandlers().stream()
                .filter(handlerMethod -> handlerMethod.getBeanType().equals(AdminAuditLogController.class))
                .map(AdminAuditCoverageTest::signature)
                .toList();

        assertThat(writeHandlers).as("감사 로그에 수정·삭제 경로가 생겼다").isEmpty();
    }

    /**
     * 추적 필터가 등록돼 있어야 감사 행의 추적 id가 채워진다 — 그 값이 비면 행과 서버 로그를
     * 맞춰 볼 수단이 사라진다. 시큐리티 체인(기본 -100)보다 앞이어야 인증 실패 로그에도 찍힌다.
     */
    @Test
    void 추적_필터가_시큐리티_체인보다_앞에_등록된다() {
        FilterRegistrationBean<?> registration =
                applicationContext.getBean("traceIdFilterRegistration", FilterRegistrationBean.class);

        assertThat(registration.getFilter()).isInstanceOf(TraceIdFilter.class);
        assertThat(registration.getOrder())
                .as("추적 필터가 시큐리티 체인(%s)보다 뒤면 인증 실패 응답에 추적 id가 없다", SECURITY_CHAIN_ORDER)
                .isLessThan(SECURITY_CHAIN_ORDER);
    }

    /** 적어도 한 건은 잡혀야 한다 — 0건이면 위 단언 셋이 모두 항진명제가 된다 */
    @Test
    void 쓰기_핸들러_수집이_비어_있지_않다() {
        assertThat(mutatingAdminHandlers())
                .as("어드민 쓰기 핸들러를 하나도 수집하지 못했다. 매핑 조회 방식이 바뀌었다")
                .isNotEmpty();
    }

    private List<HandlerMethod> mutatingAdminHandlers() {
        List<HandlerMethod> handlers = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (handlerMethod.getBeanType().getName().startsWith(ADMIN_PACKAGE) && isMutating(info)) {
                handlers.add(handlerMethod);
            }
        });
        return handlers;
    }

    private boolean isMutating(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream().anyMatch(MUTATING::contains);
    }

    private Set<String> pathVariableNames(HandlerMethod handlerMethod) {
        Set<String> names = new java.util.HashSet<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
            if (annotation == null) {
                continue;
            }
            names.add(annotation.value().isEmpty() ? parameter.getParameterName() : annotation.value());
        }
        return names;
    }

    private static String signature(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getSimpleName() + "#"
                + handlerMethod.getMethod().getName();
    }
}
