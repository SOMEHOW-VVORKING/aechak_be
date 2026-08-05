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
    fun findByBuyerId(buyerId: Long): Cart?

    // 잠금은 carts 행에만 걸린다. 하이버네이트가 for update of carts로 내보내기 때문.
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
    override fun save(cart: Cart): Cart = jpaRepository.save(cart)

    override fun findByBuyerId(buyerId: Long): Cart? = jpaRepository.findByBuyerId(buyerId)

    // fetch join이라 라인 수만큼 행이 나오지만 하이버네이트가 루트를 중복 제거한다.
    override fun findByBuyerIdForUpdate(buyerId: Long): Cart? = jpaRepository.findByBuyerIdForUpdate(buyerId).firstOrNull()

    override fun flush() = jpaRepository.flush()
}
