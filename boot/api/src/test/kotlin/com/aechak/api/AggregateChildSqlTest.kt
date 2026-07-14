package com.aechak.api

import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.CartItem
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 단방향 @OneToMany + @JoinColumn(nullable=false, updatable=false) 매핑의 SQL 동작 회귀 테스트.
 *
 * 배경: 단방향 @OneToMany는 자식 INSERT 후 FK를 다시 쓰는 잉여 UPDATE가 자식당 1개 나가는
 * 것으로 알려져 있다(Vlad Mihalcea 등). 실측 결과 nullable=false만으로는 UPDATE가 남고,
 * updatable=false까지 붙여야 INSERT-only(부모1+자식N)로 떨어진다 — 이 특성이 깨지면
 * (예: updatable=false 제거, 매핑 변경) 이 테스트가 잡는다. 기록: docs/61-entity-setup-log.md.
 *
 * updatable=false 전제: 애그리거트 자식은 부모 간 이동(reparenting)이 없다 — 생명주기 종속이라
 * 제거는 orphanRemoval(DELETE)로만 일어난다. 이 전제 자체를 스키마에 새기는 효과도 있다.
 */
@SpringBootTest(
    properties = [
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.SQL=debug",
    ],
)
class AggregateChildSqlTest {
    @PersistenceContext
    lateinit var em: EntityManager

    @Autowired
    lateinit var tx: TransactionTemplate

    private fun stats() = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    @Test
    fun `자식 2개 저장은 INSERT 3개로만 — 잉여 FK UPDATE 없음`() {
        val s = stats()
        s.clear()

        val cartId =
            tx.execute {
                val cart = Cart.create(buyerId = 1L)
                cart.addItem(CartItem.of(optionCombinationId = 10L, quantity = 2))
                cart.addItem(CartItem.of(optionCombinationId = 11L, quantity = 1))
                em.persist(cart)
                em.flush()
                cart.id
            }!!

        assertEquals(3, s.entityInsertCount, "부모 1 + 자식 2 INSERT")
        assertEquals(0, s.entityUpdateCount, "잉여 엔티티 UPDATE 없음")
        assertEquals(3, s.prepareStatementCount, "JDBC 문장 = INSERT 3개뿐 (FK UPDATE가 다시 생기면 5가 된다)")

        // 단일 품목 제거(orphanRemoval) — FK null-out UPDATE 없이 DELETE 1개
        s.clear()
        tx.execute {
            val cart = em.find(Cart::class.java, cartId)
            cart.removeItem(cart.items.first().id)
        }
        assertEquals(1, s.entityDeleteCount, "제거 품목 DELETE 1개")
        assertEquals(0, s.entityUpdateCount, "FK null-out UPDATE 없음")

        // 애그리거트 전체 삭제 — 자식 먼저 개별 DELETE 후 부모 DELETE
        s.clear()
        tx.execute {
            em.remove(em.find(Cart::class.java, cartId))
        }
        assertEquals(2, s.entityDeleteCount, "남은 자식 1 + 부모 1 DELETE")
        assertEquals(0, s.entityUpdateCount)
    }
}
