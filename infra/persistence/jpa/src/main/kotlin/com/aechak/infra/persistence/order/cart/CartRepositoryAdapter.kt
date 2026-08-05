package com.aechak.infra.persistence.order.cart

import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.repository.CartRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface CartJpaRepository : JpaRepository<Cart, Long> {
    fun existsByBuyerId(buyerId: Long): Boolean

    // 잠금은 carts 행에만 걸림. 하이버네이트가 for update of carts로 내보내기 때문.
    // 라인을 같은 쿼리로 읽어 조회를 한 번으로 줄임.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c left join fetch c._items where c.buyerId = :buyerId")
    fun findByBuyerIdForUpdate(
        @Param("buyerId") buyerId: Long,
    ): List<Cart>
}

@Repository
class CartRepositoryAdapter(
    private val jpaRepository: CartJpaRepository,
) : CartRepository {
    override fun existsByBuyerId(buyerId: Long): Boolean = jpaRepository.existsByBuyerId(buyerId)

    override fun save(cart: Cart) {
        jpaRepository.save(cart)
    }

    // fetch join이라 라인 수만큼 행이 나오지만 하이버네이트가 루트를 중복 제거함
    override fun findByBuyerIdForUpdate(buyerId: Long): Cart? = jpaRepository.findByBuyerIdForUpdate(buyerId).firstOrNull()

    override fun flush() = jpaRepository.flush()
}
