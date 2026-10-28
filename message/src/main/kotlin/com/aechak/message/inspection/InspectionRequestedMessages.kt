package com.aechak.message.inspection

import com.aechak.message.GuaranteedMessage
import java.time.Instant

/**
 * 검수 요청의 공통 규약.
 *
 * 판정에 필요한 본문을 전부 실어 보낸다(self-contained). AI 서버가 BE를 되묻지 않으므로
 * 역방향 호출 인증과 보안그룹 인바운드, 순환 의존이 생기지 않는다.
 *
 * aggregateType을 'inspection'으로 두어 전용 토픽을 쓴다. 상품·주문 이벤트와 토픽을 공유하면
 * 판정 한 건이 막힐 때 그 도메인 이벤트까지 함께 멈춘다.
 */
sealed interface InspectionRequestedMessage : GuaranteedMessage {
    val inspectionId: Long
    val targetType: InspectionTargetType
    val targetRef: Long

    /** 판정 대상 본문의 SHA-256. 같은 내용이면 판정을 재사용해 재과금을 막는다 */
    val contentHash: String

    /** 계약 버전. 컨슈머가 모르는 major를 받으면 조용히 떨어뜨리지 않고 거절해야 한다 */
    val schemaVersion: Int

    override val aggregateType: String get() = AGGREGATE_TYPE

    companion object {
        const val AGGREGATE_TYPE = "inspection"
        const val SCHEMA_VERSION = 1
    }
}

/**
 * 상품 검수 요청. 등록과 수정 모두 같은 메시지를 쓴다.
 *
 * imageUrls는 키가 아니라 공개 CDN URL 완성형이다. 키만 보내면 AI 서버가 base URL 조립 규칙을
 * 중복으로 들고 있어야 하고, 그 규칙이 BE와 어긋나면 조용히 빈 이미지를 판정한다.
 */
data class ProductInspectionRequestedMessage(
    override val inspectionId: Long,
    val productId: Long,
    val productVersionNo: Int,
    override val contentHash: String,
    val productName: String,
    val description: String?,
    val optionNames: List<String>,
    val categoryPath: List<String>,
    val imageUrls: List<String>,
    /** 직렬화 상한 때문에 description을 잘라 보냈는지 */
    val truncated: Boolean = false,
    override val schemaVersion: Int = InspectionRequestedMessage.SCHEMA_VERSION,
    override val occurredAt: Instant = Instant.now(),
) : InspectionRequestedMessage {
    override val targetType: InspectionTargetType get() = InspectionTargetType.PRODUCT
    override val targetRef: Long get() = productId
    override val orderingKey: String get() = productId.toString()
    override val eventId: String get() = "product-inspection-$inspectionId:requested"
}

/**
 * 리뷰 검수 요청.
 *
 * content는 연락처·계좌를 가린 뒤의 본문이다. 원문을 그대로 실으면 Kafka와 외부 모델 양쪽에
 * 개인정보가 평문으로 남는다. 차단된 리뷰는 애초에 요청을 만들지 않는다.
 */
data class ReviewInspectionRequestedMessage(
    override val inspectionId: Long,
    val reviewId: Long,
    val productId: Long,
    override val contentHash: String,
    val content: String,
    val rating: Int,
    val categoryPath: List<String>,
    val truncated: Boolean = false,
    override val schemaVersion: Int = InspectionRequestedMessage.SCHEMA_VERSION,
    override val occurredAt: Instant = Instant.now(),
) : InspectionRequestedMessage {
    override val targetType: InspectionTargetType get() = InspectionTargetType.REVIEW
    override val targetRef: Long get() = reviewId

    /** 상품 단위로 순서를 맞춘다. 같은 상품의 리뷰 판정이 같은 파티션으로 간다 */
    override val orderingKey: String get() = productId.toString()
    override val eventId: String get() = "review-inspection-$inspectionId:requested"
}
