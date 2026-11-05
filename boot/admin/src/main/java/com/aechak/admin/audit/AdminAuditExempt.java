package com.aechak.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 쓰기 핸들러는 감사 로그를 남기지 않는다 — 그 판단을 글로 남기는 자리.
 *
 * <p>면제를 애너테이션으로 쓰게 하는 이유: 새 어드민 쓰기 경로를 만든 사람이 감사 기록을
 * "생각하지 않고 지나가는 것"을 막는다. 커버리지 테스트는 {@link AdminAudited}도 이것도 없는
 * 핸들러에서 실패하므로, 둘 중 어느 쪽인지는 반드시 한 번 결정된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAuditExempt {

    /** 남기지 않는 이유. 비워 둘 수 없다 */
    String reason();
}
