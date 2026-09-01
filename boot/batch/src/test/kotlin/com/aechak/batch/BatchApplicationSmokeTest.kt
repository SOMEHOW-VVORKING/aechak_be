package com.aechak.batch

import com.aechak.batch.order.OrderGroupExpireJobConfig
import com.aechak.batch.order.OrderGroupExpireSchedule
import com.aechak.batch.outbox.OutboxSweepSchedule
import com.aechak.batch.support.BatchIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.batch.core.job.Job
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ConfigurableApplicationContext

/**
 * 배치 조립 스모크. 스케줄러가 쓰는 유스케이스 빈이 배치 컨텍스트에서 실제로 해소되는지를 고정함.
 * 깨지면 만료 배치와 아웃박스 스위퍼가 배포 후 기동 시점에야 죽고, 그동안 재고와 적립금이 안 돌아옴.
 */
class BatchApplicationSmokeTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var expireSchedule: OrderGroupExpireSchedule

    @Autowired
    private lateinit var sweepSchedule: OutboxSweepSchedule

    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    @Autowired
    @Qualifier(OrderGroupExpireJobConfig.ORDER_GROUP_EXPIRE_JOB)
    private lateinit var expireJob: Job

    @Test
    fun `배치 컨텍스트가 스케줄러 배선을 모두 해소한다`() {
        assertNotNull(expireSchedule, "만료 스케줄러가 없으면 만료된 주문그룹이 재고와 적립금을 계속 잡아 둔다")
        assertNotNull(sweepSchedule, "아웃박스 스위퍼가 없으면 발행에 실패한 이벤트가 다시 나가지 않는다")
        assertNotNull(expireJob, "만료 잡 배선이 풀리면 스케줄이 매 주기 잡 실행 실패 로그만 남긴다")
    }

    @Test
    fun `스케줄 빈은 전역 lazy의 예외로 기동 시 생성된다`() {
        listOf("orderGroupExpireSchedule", "outboxSweepSchedule").forEach { name ->
            assertFalse(
                context.beanFactory.getBeanDefinition(name).isLazyInit,
                "$name 이 lazy면 스케줄 등록이 없어 에러 없이 스윕만 멈춘다",
            )
        }
    }
}
