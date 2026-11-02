package com.aechak.admin.audit;

import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.audit.support.AdminAuditDetail;
import com.aechak.application.audit.usecase.AdminAuditLogUseCase;
import com.aechak.application.audit.usecase.command.RecordAdminActionCommand;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.webcommon.trace.TraceIdFilter;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link AdminAudited} 핸들러가 끝난 뒤 감사 행을 쓴다.
 *
 * <p>왜 핸들러가 끝난 뒤인가. 첫째, 조치의 트랜잭션은 파사드가 반환할 때 이미 커밋돼 있어서
 * 여기서 무엇을 실패해도 조치가 되돌아가지 않는다. 둘째, 거부당한 시도가 여기서만 보인다 —
 * 전역 예외 핸들러가 {@code BusinessException}을 실패 봉투로 번역하므로 {@code afterCompletion}의
 * 예외 인자는 보통 null이고, 성공·실패의 판정은 <b>최종 응답 상태</b>로 한다.
 *
 * <p><b>남지 않는 것</b>: 모듈 게이트가 끊은 요청(401·403)은 Security 필터 구간에서 끝나
 * DispatcherServlet에 닿지 않으므로 이 인터셉터를 지나가지 않는다. 즉 "권한이 없어 거절된 시도"는
 * 접근 로그에만 남는다. 핸들러까지 들어온 뒤의 업무 거부(이미 관리자다, 마지막 관리자다 등)는 남는다.
 */
@Slf4j
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final AdminAuditLogUseCase auditLog;

    /**
     * 요청 시작 시 컨텍스트를 비운다. 스레드 풀이 스레드를 재사용하므로, 앞 요청이 남긴 사유가
     * 남아 있으면 이번 조치의 사유 칸에 남의 사유가 들어간다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AdminAuditContextHolder.INSTANCE.clear();
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            AdminAudited audited = auditedAnnotation(handler);
            if (audited != null) {
                record(request, response, audited);
            }
        } catch (RuntimeException e) {
            // 기록 경로의 사고가 이미 끝난 조치의 응답을 바꾸지 않게 한다. 유스케이스 안의 쓰기
            // 실패는 거기서 삼켜지고, 여기서 잡는 것은 봉투를 조립하다 난 사고다.
            log.error("감사 로그 봉투 조립 실패, 이 요청의 조치는 기록되지 않았다 uri={}", request.getRequestURI(), e);
        } finally {
            AdminAuditContextHolder.INSTANCE.clear();
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, AdminAudited audited) {
        AdminAuditAction action = audited.value();
        Long actorUserId = actorUserId();
        if (actorUserId == null) {
            // 어드민 게이트를 지난 요청에는 항상 주체가 있다. 없다면 조립이 바뀐 것이므로 알려야 한다.
            log.error("감사 로그에 남길 운영자를 찾지 못했다 action={} uri={}", action, request.getRequestURI());
            return;
        }

        AdminAuditDetail detail = AdminAuditContextHolder.INSTANCE.peek();
        String targetId = resolveTargetId(request, audited, detail);
        if (targetId == null) {
            log.error(
                    "감사 로그 대상을 해결하지 못해 기록하지 않았다 action={} actorUserId={} uri={} targetVar={}",
                    action,
                    actorUserId,
                    request.getRequestURI(),
                    audited.targetVar());
            return;
        }

        int status = response.getStatus();
        auditLog.record(new RecordAdminActionCommand(
                actorUserId,
                action,
                targetType(action, detail),
                targetId,
                detail == null ? null : detail.getTargetLabel(),
                detail == null ? null : detail.getReason(),
                status < HttpStatus.BAD_REQUEST.value() ? AdminAuditResult.SUCCESS : AdminAuditResult.FAILURE,
                status,
                requestId(response),
                clientIp(request),
                LocalDateTime.now()));
    }

    /** 컨텍스트가 올린 대상이 경로 변수를 이긴다 — 본문에 있는 대상, 더 정확한 식별자가 그 쪽이다 */
    private String resolveTargetId(HttpServletRequest request, AdminAudited audited, AdminAuditDetail detail) {
        if (detail != null && detail.getTargetId() != null) {
            return detail.getTargetId();
        }
        if (audited.targetVar().isEmpty()) {
            return null;
        }
        return pathVariables(request).get(audited.targetVar());
    }

    private AdminAuditTargetType targetType(AdminAuditAction action, AdminAuditDetail detail) {
        if (detail != null && detail.getTargetType() != null) {
            return detail.getTargetType();
        }
        return action.getTargetType();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return attribute instanceof Map<?, ?> ? (Map<String, String>) attribute : Map.of();
    }

    private AdminAudited auditedAnnotation(Object handler) {
        return handler instanceof HandlerMethod handlerMethod
                ? handlerMethod.getMethodAnnotation(AdminAudited.class)
                : null;
    }

    private Long actorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
                ? principal.getUserId()
                : null;
    }

    /** 응답 헤더의 추적 id. TraceIdFilter가 성공·실패 관계없이 모든 응답에 넣는다 */
    private String requestId(HttpServletResponse response) {
        return response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    }

    /**
     * ALB 뒤라 remoteAddr은 로드밸런서다. X-Forwarded-For의 첫 홉이 클라이언트인데 그 값은
     * 클라이언트가 보낸 헤더일 수도 있으므로 <b>조사 단서로만</b> 쓴다(인가 판단에 쓰지 않는다).
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        // limit=2 — 인자 없는 split 은 뒤쪽 빈 조각을 버려서 ","·",," 같은 헤더에 빈 배열을 준다.
        // 그 상태로 [0]을 읽으면 봉투 조립이 터져 클라이언트가 보낸 헤더 한 줄로 감사 행이 사라진다.
        String firstHop = forwardedFor.split(",", 2)[0].strip();
        return firstHop.isEmpty() ? request.getRemoteAddr() : firstHop;
    }
}
