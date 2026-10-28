package com.aechak.api.inspection

import com.aechak.api.consumer.inspection.ContentVerdictConsumer
import com.aechak.application.inspection.facade.AdminProductInspectionFacade
import com.aechak.application.inspection.facade.InspectionFacade
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.facade.ProductFacade
import com.aechak.application.product.product.usecase.command.AppealProductInspectionCommand
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 검토 큐에 쓰는 **모든 경로**의 격리 수준 선언을 고정한다.
 *
 * READ COMMITTED가 떠받치는 것이 둘이다. 상품 행을 잠근 뒤에 도는 비잠금 조회가 최신 커밋을
 * 읽는 것(최신 검수 확인과 관리자 결정 확인), 그리고 비유일 인덱스의 잠금 조회가 간극을 쥐지
 * 않는 것(처음 큐에 오르는 상품 둘이 서로의 등재를 기다려 교착하는 것).
 *
 * 그 선언은 트랜잭션을 여는 쪽에만 효력이 있다. 판정 컨슈머가 트랜잭션을 먼저 열고 판정
 * 적용이 전파 REQUIRED로 합류하므로, 컨슈머가 기본 격리로 열면 합류하는 쪽의 선언은 예외도
 * 없이 조용히 무시된다. 테스트가 유스케이스를 직접 부르면 그때는 유스케이스가 가장 바깥이라
 * 선언이 살아서, 운영에서만 틀린 상태를 테스트가 가린다. 그래서 둘 다 본다.
 *
 * 보는 경로가 넷이다. 열린 검토 행의 **잠금 조회**에 닿는 쓰기 경로 전부다 — 판정 컨슈머,
 * 판정 적용, 셀러 이의 접수, 관리자 승인·반려. 그 조회는 행이 없을 때 (대상 종류, 대상 id,
 * 상태) 비유일 인덱스의 꼬리 간극을 쥐고, 그 간극이 곧 다음 큐 등재 INSERT 자리다. 넷 가운데
 * 하나만 기본 격리로 돌아도 그 경로가 도는 동안 신규 상품의 첫 등재가 전부 선다.
 *
 * 한계: 이 단언은 선언이 있다는 것까지만 증명한다. 선언이 있는데도 효력이 없는 쪽을 잡으려면
 * 그 경로를 실제로 태우고 그 트랜잭션의 격리 수준을 조회해야 한다. 상품 수정 경로는 선언이
 * 애너테이션이 아니라 트랜잭션 템플릿에 있어 여기서 보지 않는다.
 */
class VerdictPathIsolationTest {
    @Test
    fun `판정 컨슈머가 READ COMMITTED로 트랜잭션을 연다`() {
        assertEquals(
            Isolation.READ_COMMITTED,
            isolationOf(ContentVerdictConsumer::class.java, "onMessage", String::class.java),
            "운영 경계가 여기다. 기본 격리로 열면 판정 적용의 선언이 조용히 무시된다",
        )
    }

    @Test
    fun `판정 적용도 READ COMMITTED를 선언한다`() {
        assertEquals(
            Isolation.READ_COMMITTED,
            isolationOf(InspectionFacade::class.java, "applyVerdict", ApplyVerdictCommand::class.java),
            "컨슈머 밖에서 부르는 길이 가장 바깥이 될 때를 위한 선언이다",
        )
    }

    @Test
    fun `셀러 이의 접수도 READ COMMITTED를 선언한다`() {
        assertEquals(
            Isolation.READ_COMMITTED,
            isolationOf(
                ProductFacade::class.java,
                "appealMyProductInspection",
                AppealProductInspectionCommand::class.java,
            ),
            "이 경로도 열린 행이 없으면 잠금 조회 뒤에 행을 INSERT 한다 — 판정 경로와 같은 간극을 쥔다",
        )
    }

    @Test
    fun `관리자 승인과 반려도 READ COMMITTED를 선언한다`() {
        assertEquals(
            Isolation.READ_COMMITTED,
            isolationOf(
                AdminProductInspectionFacade::class.java,
                "approve",
                Long::class.javaPrimitiveType!!,
                String::class.java,
            ),
            "AI 반려를 푸는 유일한 자리라 전건에 대고 돌아간다. 기본 격리면 그때마다 첫 등재가 선다",
        )
        assertEquals(
            Isolation.READ_COMMITTED,
            isolationOf(
                AdminProductInspectionFacade::class.java,
                "reject",
                Long::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java,
            ),
            "승인과 같은 조회에 닿는다",
        )
    }

    private fun isolationOf(
        type: Class<*>,
        method: String,
        vararg parameterTypes: Class<*>,
    ): Isolation {
        val declaration =
            requireNotNull(
                type.getDeclaredMethod(method, *parameterTypes).getAnnotation(Transactional::class.java),
            ) { "$method 에 트랜잭션 선언이 없다" }
        return declaration.isolation
    }
}
