package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.repository.CartRepository
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * 영속 계약 통합 테스트. 깨지면 JPA 매핑이나 ddl-auto가 만든 UNIQUE 제약이 무너진 것임.
 * 동일 조합 1행과 회원당 장바구니 1개는 애플리케이션 로직이 아니라 이 두 제약이 바닥에서 받침.
 */
class CartPersistenceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var cartRepository: CartRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

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
    fun `수량 컬럼은 음수 저장을 거부한다`() {
        val cartId =
            tx.execute {
                val cart = Cart.create(buyerId = 1L)
                em.persist(cart)
                cart.id
            }!!

        assertThrows<PersistenceException>("음수 수량은 unsigned 컬럼 타입에 걸려야 한다") {
            tx.execute {
                em
                    .createNativeQuery(
                        "insert into cart_items (cart_id, option_combination_id, quantity, created_at, updated_at) " +
                            "values ($cartId, 10, -1, now(), now())",
                    ).executeUpdate()
            }
        }
    }

    @Test
    fun `한 구매자에 장바구니 행은 하나만 허용된다`() {
        tx.execute { em.persist(Cart.create(buyerId = 1L)) }

        assertThrows<PersistenceException>("같은 buyer_id 두 번째 장바구니는 UNIQUE 제약에 걸려야 한다") {
            tx.execute { em.persist(Cart.create(buyerId = 1L)) }
        }
    }

    /**
     * 담기 격리 수준의 회귀 방어. 스레드 두 개를 래치로 세워 순서를 강제하므로 스케줄링 운에 기대지 않음.
     * 뒤 트랜잭션이 먼저 스냅샷을 잡고, 그 뒤에 앞 트랜잭션이 수량을 올리고 커밋함.
     * REPEATABLE READ면 뒤 트랜잭션이 낡은 1을 읽어 2로 덮어써 결과가 2가 되고 READ COMMITTED면 3이 됨.
     */
    @Test
    fun `앞 트랜잭션이 커밋한 수량을 뒤 트랜잭션이 보고 누적한다`() {
        val buyerId = 42L
        val comboId = 10L
        tx.execute {
            val cart = Cart.create(buyerId)
            cart.addItem(comboId, 1)
            em.persist(cart)
        }

        val readCommitted =
            TransactionTemplate(transactionManager).apply {
                isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            }
        val snapshotTaken = CountDownLatch(1)
        val firstCommitted = CountDownLatch(1)

        val second =
            thread {
                readCommitted.execute {
                    cartRepository.existsByBuyerId(buyerId) // 이 읽기로 스냅샷이 잡힘
                    snapshotTaken.countDown()
                    firstCommitted.await()

                    cartRepository.findByBuyerIdForUpdate(buyerId)!!.addItem(comboId, 1)
                    cartRepository.flush()
                }
            }

        snapshotTaken.await()
        readCommitted.execute {
            cartRepository.findByBuyerIdForUpdate(buyerId)!!.addItem(comboId, 1)
            cartRepository.flush()
        }
        firstCommitted.countDown()
        second.join()

        val quantity =
            em
                .createQuery("select ci.quantity from CartItem ci", java.lang.Integer::class.java)
                .singleResult
                .toInt()
        assertEquals(3, quantity, "1에 1씩 두 번 더했으므로 3이어야 한다. 2면 뒤 트랜잭션이 낡은 값을 덮어쓴 것")
    }

    /**
     * 수정 격리 수준의 회귀 방어. 담기와 같은 래치 구조이고 대상만 병합으로 바꿈.
     * 두 줄을 같은 조합으로 각각 병합시키면 목적지 줄의 수량이 두 번 누적돼야 함.
     * REPEATABLE READ면 뒤 트랜잭션이 낡은 목적지 수량을 읽어 앞의 병합이 유실됨.
     */
    @Test
    fun `앞 트랜잭션이 병합한 수량을 뒤 트랜잭션이 보고 누적한다`() {
        val buyerId = 42L
        val destinationCombo = 10L
        val (firstSourceId, secondSourceId) =
            tx.execute {
                val cart = Cart.create(buyerId)
                cart.addItem(destinationCombo, 1)
                val first = cart.addItem(11L, 2)
                val second = cart.addItem(12L, 3)
                em.persist(cart)
                em.flush()
                first.id to second.id
            }!!

        val readCommitted =
            TransactionTemplate(transactionManager).apply {
                isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            }
        val snapshotTaken = CountDownLatch(1)
        val firstCommitted = CountDownLatch(1)

        val second =
            thread {
                readCommitted.execute {
                    cartRepository.existsByBuyerId(buyerId) // 이 읽기로 스냅샷이 잡힘
                    snapshotTaken.countDown()
                    firstCommitted.await()

                    val cart = cartRepository.findByBuyerIdForUpdate(buyerId)!!
                    cart.changeItemOption(cart.findItem(secondSourceId)!!, destinationCombo)
                    cartRepository.flush()
                }
            }

        snapshotTaken.await()
        readCommitted.execute {
            val cart = cartRepository.findByBuyerIdForUpdate(buyerId)!!
            cart.changeItemOption(cart.findItem(firstSourceId)!!, destinationCombo)
            cartRepository.flush()
        }
        firstCommitted.countDown()
        second.join()

        val quantity =
            em
                .createQuery("select ci.quantity from CartItem ci where ci.optionCombinationId = :c", java.lang.Integer::class.java)
                .setParameter("c", destinationCombo)
                .singleResult
                .toInt()
        assertEquals(6, quantity, "1에 2와 3을 병합했으므로 6이어야 한다. 낡은 값을 읽으면 앞의 병합이 유실됨")
    }
}
