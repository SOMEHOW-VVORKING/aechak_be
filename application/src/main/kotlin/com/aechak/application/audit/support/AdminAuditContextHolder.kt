package com.aechak.application.audit.support

import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType

/**
 * 한 요청이 처리되는 동안 감사 기록에 보탤 의미를 모으는 자리.
 *
 * 왜 스레드 지역 변수인가: 사유는 요청 본문(web)에 있고 대상 이름은 애그리거트(application)에
 * 있는데, 기록을 실제로 쓰는 자리는 조치가 끝난 뒤(인터셉터)다. 그 셋을 반환 타입으로 꿰면
 * 조치마다 반환 타입이 감사용으로 늘어난다 — 유스케이스 계약이 기록 메커니즘에 끌려간다.
 *
 * [SecurityContextHolder][org.springframework.security.core.context.SecurityContextHolder]와 같은
 * 모양이고 같은 제약을 갖는다 — **요청 스레드 밖으로 전파되지 않는다.** @Async·배치·카프카 소비자가
 * 수행하는 조치는 이 자리로 의미를 올릴 수 없으므로, 그 경로가 생기면 커맨드를 직접 만들어
 * [com.aechak.application.audit.usecase.AdminAuditLogUseCase]를 부른다.
 *
 * 비우기는 요청 경계가 책임진다(인터셉터의 preHandle·afterCompletion). 스레드 풀이 스레드를
 * 재사용하므로 비우지 않으면 다음 요청이 남의 사유를 기록한다.
 */
object AdminAuditContextHolder {
    private val holder = ThreadLocal<AdminAuditDetail>()

    /** 대상을 지정한다. 경로 변수로 이미 잡히는 대상에도 부를 수 있다 — 더 정확한 쪽이 이긴다 */
    fun target(
        targetType: AdminAuditTargetType,
        targetId: String,
    ) {
        mutate { it.copy(targetType = targetType, targetId = targetId) }
    }

    /** 기록 시점의 대상 이름. 대상이 이름을 바꾸거나 지워진 뒤에도 그때의 이름이 남는다 */
    fun label(targetLabel: String?) {
        mutate { it.copy(targetLabel = targetLabel) }
    }

    /** 운영자가 적은 사유. 반려·거부처럼 사유가 계약에 있는 조치만 올린다 */
    fun reason(reason: String?) {
        mutate { it.copy(reason = reason) }
    }

    fun peek(): AdminAuditDetail? = holder.get()

    fun clear() {
        holder.remove()
    }

    private fun mutate(transform: (AdminAuditDetail) -> AdminAuditDetail) {
        holder.set(transform(holder.get() ?: AdminAuditDetail()))
    }
}
