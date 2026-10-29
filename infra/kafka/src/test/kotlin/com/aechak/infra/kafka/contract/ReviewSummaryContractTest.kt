package com.aechak.infra.kafka.contract

import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.user.pet.enums.Species
import com.aechak.infra.kafka.config.MessagingJacksonConfig
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [단위] 파이썬 AI 서버와 공유하는 리뷰 요약 계약을 고정한다.
 *
 * review-selection-vectors.json -> AI 서버 tests/contract의 체급 묶음 골든 벡터 사본
 * 깨지면 계약이 바뀐 것이므로 AI 서버 쪽과 같은 커밋에서 함께 고쳐야 한다.
 */
class ReviewSummaryContractTest {
    private val mapper: ObjectMapper = MessagingJacksonConfig().messagingObjectMapper()

    @Test
    fun `체급 묶음 키가 AI 서버 골든 벡터와 같다`() {
        val golden = mapper.readTree(readFixture("review-selection-vectors.json"))

        val goldenSegmentKeys: List<String> = golden.get("segmentKeys").values().map { it.asString() }
        assertEquals(goldenSegmentKeys, PetWeightBand.entries.map { it.segmentKey })
        for (vector in golden["vectors"]) {
            for ((side, expected) in listOf("viewer" to "viewerSegment", "reviewer" to "reviewerSegment")) {
                assertEquals(textOrNull(vector[expected]), segmentKeyOf(vector[side]), "${vector["name"].asString()} $side")
            }
        }
    }

    private fun segmentKeyOf(facts: JsonNode): String? {
        if (facts.isNull) return null
        val weight = facts["weightKg"].takeUnless { it.isNull }?.let { BigDecimal(it.asString()) }
        return PetWeightBand.of(Species.valueOf(facts["species"].asString()), weight)?.segmentKey
    }

    private fun textOrNull(node: JsonNode): String? = node.takeUnless { it.isNull }?.asString()

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/contract/$name")) { name }
            .readAllBytes()
            .decodeToString()
            .trimEnd('\n')
}
