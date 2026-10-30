package com.aechak.admin.audit;

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 핸들러가 수행하는 조치를 감사 로그에 남긴다.
 *
 * <p>기록은 {@link AdminAuditInterceptor}가 핸들러가 끝난 뒤에 쓴다 — 조치의 트랜잭션이 이미
 * 커밋된 자리라서 기록 실패가 조치를 되돌릴 수 없고, 거부당한 시도(예외·4xx)도 같은 자리에서
 * 보이기 때문이다. 유스케이스마다 기록 호출을 흩뿌리지 않는 이유는 그 방식이 **빠뜨리기 쉽고,
 * 빠뜨린 것이 보이지 않기** 때문이다.
 *
 * <p>인터셉터가 스스로 알 수 있는 것은 봉투(누가·언제·무슨 조치·결과·IP·추적 id)까지다.
 * 대상과 사유는 요청과 애그리거트에만 있으므로 둘 중 하나로 올린다.
 *
 * <ul>
 *   <li>{@link #targetVar()} — 대상 식별자가 경로 변수에 있는 경우. 이름을 적으면 끝이다.
 *   <li>{@link com.aechak.application.audit.support.AdminAuditContextHolder} — 대상이 본문에
 *       있거나 사유·대상 이름을 함께 남겨야 하는 경우. 컨텍스트에 올린 값이 경로 변수를 이긴다.
 * </ul>
 *
 * <p>쓰기 핸들러에 이 애너테이션이나 {@link AdminAuditExempt} 중 하나가 반드시 있어야 한다 —
 * 둘 다 없으면 {@code AdminAuditCoverageTest}가 빌드를 끊는다. 그게 "빠뜨림"에 대한 답이다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudited {

    AdminAuditAction value();

    /**
     * 대상 식별자를 담은 경로 변수 이름. 비우면 컨텍스트가 대상을 올려야 한다.
     *
     * <p>이름이 그 핸들러의 실제 {@code @PathVariable}인지도 커버리지 테스트가 본다 — 오타는
     * 런타임에 "대상 미해결"로만 드러나고 그때는 이미 기록이 비어 있다.
     */
    String targetVar() default "";
}
