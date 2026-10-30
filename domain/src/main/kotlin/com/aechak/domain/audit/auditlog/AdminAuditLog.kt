package com.aechak.domain.audit.auditlog

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 운영자 조치 한 건의 기록 — 누가(운영자), 언제, 무엇에(대상 종류·식별자), 무슨 조치를, 왜(사유), 어떻게 끝났나.
 *
 * **고칠 수 없다.** 전 필드가 `val`이고 상태를 바꾸는 메서드가 없다. 리포지토리 포트도 [append]만 연다 —
 * 사후에 고칠 수 있는 기록은 분쟁에서 증거가 되지 못하므로 수정·삭제 경로를 만들지 않는 것이 이 애그리거트의 전부다.
 * [BaseEntity]의 updated_at은 갱신 경로가 없어 created_at과 영원히 같다. 전 테이블 공통 규약을 깨지 않으려 그대로 둔다.
 *
 * 이벤트 수집 베이스(AggregateRoot)를 상속하지 않는다 — 이 행은 다른 도메인의 관심사를 만들지 않고,
 * 쓸 일이 없는 수집 능력을 물려받으면 "감사 로그가 이벤트를 쏜다"는 오해의 자리가 생긴다.
 *
 * [actorUserId]는 users.id 값 참조(BC 밖이라 FK 없음). 조치를 한 사람이 탈퇴해도 행은 남아야 하므로
 * 조회는 LEFT JOIN으로 붙인다.
 */
@Entity
@Table(
    name = "admin_audit_logs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_admin_audit_logs_public_id", columnNames = ["public_id"]),
    ],
    // 마이그레이션(V56)의 인덱스를 같은 이름·같은 칼럼 순서로 모두 선언한다. 한쪽만 두면
    // ddl-auto로 스키마를 만드는 테스트·로컬만 인덱스 없이 돌아 조회 계획이 환경 간에 갈린다.
    indexes = [
        Index(name = "ix_admin_audit_logs_recent", columnList = "acted_at, id"),
        Index(name = "ix_admin_audit_logs_actor", columnList = "actor_user_id, id"),
        Index(name = "ix_admin_audit_logs_target", columnList = "target_type, target_id, id"),
    ],
)
class AdminAuditLog protected constructor(
    @Column(nullable = false, updatable = false)
    val actorUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = ACTION_COLUMN_LENGTH)
    val action: AdminAuditAction,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = TARGET_TYPE_COLUMN_LENGTH)
    val targetType: AdminAuditTargetType,
    @Column(nullable = false, updatable = false, length = TARGET_ID_MAX)
    val targetId: String,
    /** 기록 시점의 사람이 읽는 대상 이름. 대상이 이름을 바꾸거나 지워져도 그때의 이름이 남는다 */
    @Column(updatable = false, length = TARGET_LABEL_MAX)
    val targetLabel: String?,
    @Column(updatable = false, length = REASON_MAX)
    val reason: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = RESULT_COLUMN_LENGTH)
    val result: AdminAuditResult,
    /** 조치 요청이 받은 HTTP 상태. 실패 행에서 "무엇이 거부했나"의 첫 단서다 */
    @Column(nullable = false, updatable = false)
    val httpStatus: Int,
    /** X-Trace-Id. 서버 로그와 이 행을 맞춰 보는 키 */
    @Column(updatable = false, length = REQUEST_ID_MAX)
    val requestId: String?,
    /** 접속 IP. IPv6 전개형까지 들어갈 길이를 둔다 */
    @Column(updatable = false, length = CLIENT_IP_MAX)
    val clientIp: String?,
    @Column(nullable = false, updatable = false)
    val actedAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = Ulid.LENGTH)
    val publicId: String = Ulid.generate()

    companion object {
        const val ACTION_COLUMN_LENGTH = 60
        const val TARGET_TYPE_COLUMN_LENGTH = 40
        const val RESULT_COLUMN_LENGTH = 10
        const val TARGET_ID_MAX = 64
        const val TARGET_LABEL_MAX = 200
        const val REASON_MAX = 500
        const val REQUEST_ID_MAX = 64
        const val CLIENT_IP_MAX = 45

        /**
         * 조치 한 건을 기록한다. 조치와 대상 종류의 짝은 결과와 무관하게 대조한다 —
         * 둘 다 코드가 정하는 값이라 어긋나면 그건 조립 오류다.
         *
         * 대상 식별자의 **종류 대조는 성공 행에만** 적용한다. 거부된 시도의 식별자는 요청이
         * 들고 온 값 그대로여서 형식부터 틀려 있을 수 있고, 거기서 막으면 "누가 무엇을 하려다
         * 거부당했나"의 행이 통째로 사라진다 — 막아야 할 쪽은 잘못된 식별자가 아니라 기록의 공백이다.
         *
         * 사유·대상 이름·실패 행의 식별자는 저장 길이로 자른다. 기록이 길이 때문에 통째로
         * 실패하면 조치 자체는 성공했는데 흔적만 사라지는, 가장 나쁜 조합이 된다.
         */
        fun record(
            actorUserId: Long,
            action: AdminAuditAction,
            targetType: AdminAuditTargetType,
            targetId: String,
            targetLabel: String?,
            reason: String?,
            result: AdminAuditResult,
            httpStatus: Int,
            requestId: String?,
            clientIp: String?,
            actedAt: LocalDateTime,
        ): AdminAuditLog {
            require(actorUserId > 0L) { "조치한 운영자를 알 수 없는 기록은 남기지 않습니다." }
            require(action.targetType == targetType) {
                "조치 $action 의 대상 종류는 ${action.targetType} 인데 $targetType 로 기록하려 했습니다."
            }
            require(targetId.isNotBlank()) { "대상을 알 수 없는 기록은 남기지 않습니다." }
            require(result != AdminAuditResult.SUCCESS || targetType.idKind.accepts(targetId)) {
                "$targetType 의 식별자는 ${targetType.idKind} 여야 합니다."
            }
            return AdminAuditLog(
                actorUserId = actorUserId,
                action = action,
                targetType = targetType,
                targetId = targetId.take(TARGET_ID_MAX),
                targetLabel = targetLabel?.take(TARGET_LABEL_MAX),
                reason = reason?.take(REASON_MAX),
                result = result,
                httpStatus = httpStatus,
                requestId = requestId?.take(REQUEST_ID_MAX),
                clientIp = clientIp?.take(CLIENT_IP_MAX),
                actedAt = actedAt,
            )
        }
    }
}
