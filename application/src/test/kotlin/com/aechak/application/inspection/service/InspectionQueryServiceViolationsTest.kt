package com.aechak.application.inspection.service

import com.aechak.application.inspection.port.ViolationDeserializer
import com.aechak.application.inspection.port.view.ViolationView
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [단위] 관점별 위반 행을 합치고 정렬하는 규칙을 고정한다.
 *
 * 깨지면 조회할 때마다 목록 순서가 달라지거나, 같은 위반이 두 번 보이거나, 읽을 수 있는 제안까지 사라진다.
 */
class InspectionQueryServiceViolationsTest {
    private val findings = FakeInspectionFindingRepository()
    private val deserializer = FakeViolationDeserializer()
    private val service =
        InspectionQueryService(
            inspections = unused(),
            reviewQueue = unused(),
            findings = findings,
            violationDeserializer = deserializer,
        )

    @Test
    fun `관점별 행이 없으면 빈 목록이다`() {
        assertEquals(emptyList(), service.findViolations(INSPECTION_ID), "행이 없는 것은 오류가 아니다")
    }

    @Test
    fun `위반이 없는 행은 건너뛴다`() {
        givenRowWithoutViolations(rowId = 1)

        assertEquals(emptyList(), service.findViolations(INSPECTION_ID), "위반 없는 관점은 violations가 null로 저장된다")
    }

    @Test
    fun `여러 행의 위반을 spanStart 오름차순으로 돌려준다`() {
        givenRow(rowId = 1, view("MEDICAL_CLAIM", 30, "다"), view("SPAM", 0, "가"))
        givenRow(rowId = 2, view("OFF_PLATFORM", 10, "나"))

        assertEquals(
            listOf(view("SPAM", 0, "가"), view("OFF_PLATFORM", 10, "나"), view("MEDICAL_CLAIM", 30, "다")),
            service.findViolations(INSPECTION_ID),
            "관점별 행을 합쳐도 글 안의 위치 순서여야 한다",
        )
    }

    @Test
    fun `spanStart가 null이면 뒤로 보낸다`() {
        givenRow(rowId = 1, view("BANNED_INGREDIENT", null, "자일리톨"), view("SPAM", 7, "도배"))

        assertEquals(
            listOf(view("SPAM", 7, "도배"), view("BANNED_INGREDIENT", null, "자일리톨")),
            service.findViolations(INSPECTION_ID),
            "위치를 모르는 위반은 위치가 있는 위반 뒤에 와야 한다",
        )
    }

    @Test
    fun `spanStart가 같으면 category와 quote 순서로 돌려준다`() {
        givenRow(
            rowId = 1,
            view("SPAM", 5, "가"),
            view("ABUSE", 5, null),
            view("ABUSE", 5, "나"),
            view("ABUSE", 5, "가"),
        )

        assertEquals(
            listOf(view("ABUSE", 5, "가"), view("ABUSE", 5, "나"), view("ABUSE", 5, null), view("SPAM", 5, "가")),
            service.findViolations(INSPECTION_ID),
            "조회할 때마다 같은 순서여야 하고 quote가 null이어도 실패하면 안 된다",
        )
    }

    @Test
    fun `같은 분류와 구절은 한 건으로 합치고 가장 앞선 위치를 남긴다`() {
        givenRow(rowId = 1, view("MEDICAL_CLAIM", 20, "완치"))
        givenRow(rowId = 2, view("MEDICAL_CLAIM", 5, "완치"))

        assertEquals(
            listOf(view("MEDICAL_CLAIM", 5, "완치")),
            service.findViolations(INSPECTION_ID),
            "관점이 달라도 같은 위반을 두 번 보여 주면 안 된다",
        )
    }

    @Test
    fun `분류나 구절이 다르면 합치지 않는다`() {
        givenRow(
            rowId = 1,
            view("ABUSE", 0, "가"),
            view("ABUSE", 1, "나"),
            view("SPAM", 2, "다"),
            view("MEDICAL_CLAIM", 3, "다"),
        )

        assertEquals(
            listOf(view("ABUSE", 0, "가"), view("ABUSE", 1, "나"), view("SPAM", 2, "다"), view("MEDICAL_CLAIM", 3, "다")),
            service.findViolations(INSPECTION_ID),
            "분류와 구절이 모두 같을 때만 합쳐야 한다",
        )
    }

    @Test
    fun `합칠 때 제안이 없는 쪽보다 있는 쪽을 남긴다`() {
        givenRow(rowId = 1, view("MEDICAL_CLAIM", 5, "완치", suggestion = null))
        givenRow(rowId = 2, view("MEDICAL_CLAIM", 9, "완치", suggestion = "도움을 줄 수 있는"))

        assertEquals(
            listOf(view("MEDICAL_CLAIM", 5, "완치", suggestion = "도움을 줄 수 있는")),
            service.findViolations(INSPECTION_ID),
            "먼저 저장된 행에 제안이 없다고 제안을 잃으면 안 된다",
        )
    }

    @Test
    fun `제안이 서로 다르면 먼저 저장된 행의 제안을 남긴다`() {
        givenRow(rowId = 2, view("MEDICAL_CLAIM", 5, "완치", suggestion = "나중"))
        givenRow(rowId = 1, view("MEDICAL_CLAIM", 5, "완치", suggestion = "먼저"))

        assertEquals(
            listOf(view("MEDICAL_CLAIM", 5, "완치", suggestion = "먼저")),
            service.findViolations(INSPECTION_ID),
            "저장소가 돌려주는 순서와 상관없이 행 id가 작은 쪽을 남겨야 한다",
        )
    }

    @Test
    fun `같은 행 안에서는 앞선 원소의 제안을 남긴다`() {
        givenRow(
            rowId = 1,
            view("MEDICAL_CLAIM", 5, "완치", suggestion = "앞"),
            view("MEDICAL_CLAIM", 5, "완치", suggestion = "뒤"),
        )

        assertEquals(
            listOf(view("MEDICAL_CLAIM", 5, "완치", suggestion = "앞")),
            service.findViolations(INSPECTION_ID),
            "같은 행 안에서는 저장 순서를 따라야 한다",
        )
    }

    @Test
    fun `공백뿐인 제안은 null로 돌려준다`() {
        givenRow(rowId = 1, view("ABUSE", 0, "가", suggestion = ""), view("ABUSE", 1, "나", suggestion = "   "))

        assertEquals(
            listOf(view("ABUSE", 0, "가", suggestion = null), view("ABUSE", 1, "나", suggestion = null)),
            service.findViolations(INSPECTION_ID),
            "빈 제안을 내려주면 화면에 수정 제안이라는 이름만 남는다",
        )
    }

    @Test
    fun `합칠 때 공백뿐인 제안은 없는 제안으로 본다`() {
        givenRow(rowId = 1, view("MEDICAL_CLAIM", 5, "완치", suggestion = "   "))
        givenRow(rowId = 2, view("MEDICAL_CLAIM", 5, "완치", suggestion = "도움을 줄 수 있는"))

        assertEquals(
            listOf(view("MEDICAL_CLAIM", 5, "완치", suggestion = "도움을 줄 수 있는")),
            service.findViolations(INSPECTION_ID),
            "공백 제안이 실제 제안을 가리면 안 된다",
        )
    }

    @Test
    fun `읽지 못한 행만 빼고 나머지를 돌려준다`() {
        givenUnreadableRow(rowId = 1)
        givenRow(rowId = 2, view("SPAM", 0, "도배"))

        assertEquals(
            listOf(view("SPAM", 0, "도배")),
            service.findViolations(INSPECTION_ID),
            "한 행을 읽지 못했다고 읽을 수 있는 위반까지 버리면 안 된다",
        )
    }

    @Test
    fun `모든 행을 읽지 못하면 빈 목록이다`() {
        givenUnreadableRow(rowId = 1)
        givenUnreadableRow(rowId = 2)

        assertEquals(emptyList(), service.findViolations(INSPECTION_ID), "읽기 실패가 조회 실패로 번지면 안 된다")
    }

    private fun givenRow(
        rowId: Long,
        vararg violations: ViolationView,
    ) {
        val stored = "stored-$rowId"
        deserializer.byStored[stored] = violations.toList()
        findings.rows += row(rowId, stored)
    }

    private fun givenUnreadableRow(rowId: Long) {
        val stored = "unreadable-$rowId"
        deserializer.byStored[stored] = null
        findings.rows += row(rowId, stored)
    }

    private fun givenRowWithoutViolations(rowId: Long) {
        findings.rows += row(rowId, stored = null)
    }

    private fun row(
        rowId: Long,
        stored: String?,
    ): InspectionFinding =
        InspectionFinding
            .of(INSPECTION_ID, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.VIOLATION, violations = stored)
            .also { finding ->
                InspectionFinding::class.java
                    .getDeclaredField("id")
                    .apply { isAccessible = true }
                    .setLong(finding, rowId)
            }

    private fun view(
        category: String,
        spanStart: Int?,
        quote: String?,
        suggestion: String? = null,
    ) = ViolationView(category, spanStart, quote, suggestion)

    /** 넣어 준 순서 그대로 돌려줌. 저장소의 정렬에 기대지 않는지 보려는 것임 */
    private class FakeInspectionFindingRepository : InspectionFindingRepository {
        val rows = mutableListOf<InspectionFinding>()

        override fun upsert(finding: InspectionFinding): InspectionFinding = error("쓰지 않음")

        override fun findByInspectionId(inspectionId: Long): List<InspectionFinding> = rows.filter { it.inspectionId == inspectionId }

        override fun findOne(
            inspectionId: Long,
            perspective: InspectionPerspectiveKind,
        ): InspectionFinding? = error("쓰지 않음")
    }

    private class FakeViolationDeserializer : ViolationDeserializer {
        val byStored = mutableMapOf<String, List<ViolationView>?>()

        override fun fromJson(json: String): List<ViolationView>? = byStored.getValue(json)
    }

    private companion object {
        const val INSPECTION_ID = 7L

        inline fun <reified T : Any> unused(): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
                error("이 테스트에서 쓰지 않는 의존이 호출됨: ${method.name}")
            } as T
    }
}
