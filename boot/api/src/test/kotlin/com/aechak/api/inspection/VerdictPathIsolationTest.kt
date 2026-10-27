package com.aechak.api.inspection

import com.aechak.api.consumer.inspection.ContentVerdictConsumer
import com.aechak.application.inspection.facade.InspectionFacade
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 판정 적용 경로의 격리 수준 선언을 고정한다.
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
 * 한계: 이 단언은 선언이 있다는 것까지만 증명한다. 선언이 있는데도 효력이 없는 쪽을 잡으려면
 * 컨슈머 경로를 실제로 태우고 그 트랜잭션의 격리 수준을 조회해야 한다.
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
