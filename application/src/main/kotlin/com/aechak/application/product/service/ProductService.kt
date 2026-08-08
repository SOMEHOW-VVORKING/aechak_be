package com.aechak.application.product.service

import com.aechak.application.product.port.ProductCatalogCondition
import com.aechak.application.product.port.ProductCatalogQueryPort
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.port.ProductDetailQueryPort
import com.aechak.application.product.port.ProductOptionsQueryPort
import com.aechak.application.product.port.view.ProductCatalogDetailView
import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.application.product.port.view.ProductImageView
import com.aechak.application.product.port.view.ProductOptionsView
import com.aechak.application.product.support.ProductCursorCodec
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.like.repository.ProductLikeRepository
import com.aechak.domain.product.product.repository.ProductRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProductService(
    private val productCatalogQueryPort: ProductCatalogQueryPort,
    private val productDetailQueryPort: ProductDetailQueryPort,
    private val productOptionsQueryPort: ProductOptionsQueryPort,
    private val categoryRepository: CategoryRepository,
    private val productLikeRepository: ProductLikeRepository,
    private val productRepository: ProductRepository,
) {
    fun getVisiblePage(
        query: ProductSearchQuery,
        now: LocalDateTime,
    ): CursorPageResult<ProductCatalogView> {
        validateCategoryFilter(query.categoryId)
        val anchor = query.cursor?.let { resolveCursor(it, query.sort, query.categoryId, now) }
        // PRICE_ASC 순회는 첫 페이지 시각으로 유효가격 뷰를 고정
        // 페이지 요청 사이 시간 경과로 할인 경계가 지나며 keyset이 어긋나 생기는 중복, 누락을 차단
        // 카드 표시가는 Facade의 현재 시각을 그대로 써 만료 할인가를 계속 보여주지 않음
        val queryNow = anchor?.anchorNow ?: now
        val fetched =
            productCatalogQueryPort.findVisiblePage(
                ProductCatalogCondition(
                    categoryId = query.categoryId,
                    sort = query.sort,
                    lastId = anchor?.lastId,
                    lastPrice = anchor?.lastPrice,
                    limit = query.size + 1,
                    now = queryNow,
                ),
            )
        val hasNext = fetched.size > query.size
        val page = if (hasNext) fetched.take(query.size) else fetched
        return CursorPageResult(
            items = page,
            // 첫 페이지에서만 총개수 게산
            totalCount = if (query.cursor == null) productCatalogQueryPort.countVisible(query.categoryId) else null,
            nextCursor =
                if (hasNext) {
                    val last = page.last()
                    ProductCursorCodec.encode(
                        query.sort,
                        query.categoryId,
                        last.publicId,
                        last.sortPriceAtAnchor,
                        queryNow,
                    )
                } else {
                    null
                },
            hasNext = hasNext,
        )
    }

    /** 노출 조건을 통과한 상세 조회 */
    fun getVisibleDetail(publicId: String): ProductCatalogDetailView =
        productDetailQueryPort.findVisibleDetail(publicId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND) // 보안을 위해 미존재, 미노출 무관하게 모두 404 반환

    fun getImages(productId: Long): List<ProductImageView> = productDetailQueryPort.findImagesByProductId(productId)

    /** 노출 조건을 통과한 옵션 조회 */
    fun getVisibleOptions(publicId: String): ProductOptionsView =
        productOptionsQueryPort.findVisibleOptions(publicId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND) // 보안을 위해 미존재, 미노출 무관하게 모두 404 반환

    fun isLiked(
        productId: Long,
        userId: Long,
    ): Boolean = productLikeRepository.existsByProductIdAndUserId(productId, userId)

    /** 카테고리 필터는 중분류(depth 2)까지만 허용 */
    private fun validateCategoryFilter(categoryId: Long?) {
        if (categoryId == null) return
        val category =
            categoryRepository.findActiveById(categoryId)
                ?: throw BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND)
        if (category.depth != Category.MID_DEPTH) {
            throw BusinessException(ProductErrorCode.INVALID_CATEGORY_FILTER)
        }
    }

    /** 커서의 publicId를 내부 id로 해석 */
    private fun resolveCursor(
        raw: String,
        sort: ProductCatalogSort,
        categoryId: Long?,
        now: LocalDateTime,
    ): CursorAnchor {
        val decoded = ProductCursorCodec.decode(raw, sort)
        // 다른 카테고리 필터에서 받은 커서 재사용 차단
        if (decoded.categoryId != categoryId) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        decoded.anchorNow?.let {
            // 미래 시각 커서만 거절
            if (it.isAfter(now)) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR)
            }
        }
        val lastId =
            productRepository.findIdByPublicId(decoded.publicId)
                ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return CursorAnchor(lastId = lastId, lastPrice = decoded.lastPrice, anchorNow = decoded.anchorNow)
    }

    private data class CursorAnchor(
        val lastId: Long,
        val lastPrice: Long?,
        val anchorNow: LocalDateTime?,
    )
}
