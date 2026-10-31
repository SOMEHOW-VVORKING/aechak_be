package com.aechak.infra.client.ai

/**
 * AI 초안 계약의 와이어 모양. **이 모듈 밖으로 새지 않는다.**
 *
 * 응답 쪽은 전 필드에 기본값을 준다 — AI가 usage·guardDrops·itemSignal·image처럼 우리가 읽지
 * 않는 운영 축을 늘 함께 보내고, 그쪽에 필드가 하나 더 붙는 날 역직렬화가 터지면 셀러 화면이
 * 깨진다. 우리가 읽는 다섯(state·draft·fieldStatus·basis·sellerNotice)과 로그에만 쓰는
 * 넷(requestId·skipReason·modelCalled·usage)만 선언한다.
 */
internal data class AiDraftRequestBody(
    val requestId: String,
    val productName: String,
    val categoryId: Long?,
    val lockedFields: AiLockedFields?,
    /** 어긋나면 AI가 409로 거절한다 — 모르는 계약을 조용히 처리하지 않는 쪽이 맞다 */
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class AiLockedFields(
    val description: String?,
    val optionGroups: List<AiOptionGroup>?,
)

internal data class AiOptionGroup(
    val name: String? = null,
    val values: List<String> = emptyList(),
)

internal data class AiDraftResponseBody(
    val state: String = "FAILED",
    val draft: AiDraft = AiDraft(),
    val fieldStatus: Map<String, String> = emptyMap(),
    val basis: Map<String, String> = emptyMap(),
    val sellerNotice: String? = null,
    // --- 셀러에게 주지 않고 로그로만 쓰는 운영 축 ---
    val requestId: String = "",
    val skipReason: String? = null,
    val modelCalled: Boolean = false,
    val reusedFromCache: Boolean = false,
    val usage: List<AiDraftUsage> = emptyList(),
)

internal data class AiDraft(
    val categoryId: Long? = null,
    val description: String? = null,
    val optionGroups: List<AiOptionGroup>? = null,
    val optionCombinations: List<AiOptionCombination>? = null,
)

internal data class AiOptionCombination(
    val optionValues: List<String> = emptyList(),
    val additionalPrice: Long? = null,
    val stockQuantity: Int? = null,
)

/** 모델 비용 원장. 셀러 화면에 내보내지 않고 우리 로그에 한 줄로 적는다. */
internal data class AiDraftUsage(
    val costMicroUsd: Long = 0,
    val latencyMs: Long = 0,
)
