package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.cart.Cart
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 영속 계약 통합 테스트. 깨지면 JPA 매핑이나 ddl-auto가 만든 UNIQUE 제약이 무너진 것임.
 * 동일 조합 1행과 회원당 장바구니 1개는 애플리케이션 로직이 아니라 이 두 제약이 바닥에서 받침.
 */
class CartPersistenceIntegrationTest : IntegrationTestBase() {
    @Test
    fun `장바구니 저장 후 재조회하면 담긴 품목이 복원된다`() {
        val cartId =
            tx.execute {
                val cart = Cart.create(buyerId = 1L)
                cart.addItem(optionCombinationId = 10L, quantity = 2)
                em.persist(cart)
                cart.id
            }!!

        tx.execute {
            val found = em.find(Cart::class.java, cartId)
            assertEquals(1, found.items.size, "담은 품목 1개가 복원돼야 한다")
            assertEquals(10L, found.items[0].optionCombinationId, "옵션 조합 id가 복원돼야 한다")
            assertEquals(2, found.items[0].quantity, "수량이 복원돼야 한다")
        }
    }

    @Test
    fun `같은 장바구니에 같은 옵션 조합 행은 UNIQUE 제약이 막는다`() {
        val cartId =
            tx.execute {
                val cart = Cart.create(buyerId = 1L)
                em.persist(cart)
                cart.id
            }!!
        val insertRow =
            "insert into cart_items (cart_id, option_combination_id, quantity, created_at, updated_at) " +
                "values ($cartId, 10, 1, now(), now())"
        tx.execute { em.createNativeQuery(insertRow).executeUpdate() }

        assertThrows<PersistenceException>("동일 (cart_id, option_combination_id) 두 번째 행은 UNIQUE 제약에 걸려야 한다") {
            tx.execute { em.createNativeQuery(insertRow).executeUpdate() }
        }
    }

    @Test
    fun `한 구매자에 장바구니 행은 하나만 허용된다`() {
        tx.execute { em.persist(Cart.create(buyerId = 1L)) }

        assertThrows<PersistenceException>("같은 buyer_id 두 번째 장바구니는 UNIQUE 제약에 걸려야 한다") {
            tx.execute { em.persist(Cart.create(buyerId = 1L)) }
        }
    }
}
