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
import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductUpdateResult
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.like.repository.ProductLikeRepository
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.ProductOptions
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import com.aechak.domain.product.option.repository.OptionGroupRepository
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.repository.ProductRepository
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.product.version.repository.ProductVersionRepository
import org.springframework.dao.DataIntegrityViolationException
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
    private val optionGroupRepository: OptionGroupRepository,
    private val optionCombinationRepository: OptionCombinationRepository,
    private val productVersionRepository: ProductVersionRepository,
    private val productStatsRepository: ProductStatsRepository,
) {
    fun register(
        command: RegisterProductCommand,
        options: ProductOptions,
    ): ProductVersion {
        val product = productRepository.save(command.toEntity(loadLeafCategory(command.categoryId)))
        // 집계는 조건부 원자 UPDATE로만 갱신해서 행이 없으면 첫 리뷰가 0행 갱신으로 조용히 사라짐
        productStatsRepository.save(ProductStats.create(product.id))
        registerOptions(product, options)
        return productVersionRepository.save(ProductVersion.create(product, command.thumbnailImageKey))
    }

    fun getCurrentImageKeys(
        productPublicId: String,
        sellerId: Long,
    ): Set<String> {
        val product =
            productRepository.findByPublicIdAndSellerId(productPublicId, sellerId)
                ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        return (listOfNotNull(product.representativeImageKey) + product.currentImages.map { it.storageKey }).toSet()
    }

    fun updateProduct(command: UpdateProductCommand): ProductUpdateResult {
        val product =
            productRepository.findByPublicIdAndSellerId(command.productPublicId, command.sellerId)
                ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val category = loadLeafCategory(command.categoryId)

        val nextVersionNo = (productVersionRepository.findLastVersionNo(product.id) ?: 0) + 1
        val before = editableStateOf(product)
        product.edit(
            category = category,
            name = command.productName,
            description = command.description,
            regularPrice = command.regularPrice,
            discountPrice = command.discountPrice,
            discountStartAt = command.discountStartAt,
            discountEndAt = command.discountEndAt,
        )
        product.replaceImages(
            command.thumbnailImageKey,
            command.additionalImageKeys,
            command.detailImageKeys,
            nextVersionNo,
        )
        val after = editableStateOf(product)

        productRepository.saveNow(product) // updatedAt을 갱신 하기 위함
        if (before != after) {
            appendVersion(product, nextVersionNo)
        }
        return ProductUpdateResult.of(product)
    }

    /**
     * 수정 가능한 필드들의 현재 값 스냅샷. 반영 전후 둘을 비교해 달라졌을 때만 버전을 남김.
     * 대표 이미지 키가 따로 없는 건 currentImages 안에 REPRESENTATIVE 행으로 이미 들어 있기 때문.
     */
    private fun editableStateOf(product: Product): List<Any?> =
        listOf(
            product.category.id,
            product.name,
            product.description,
            product.regularPrice,
            product.discountPrice,
            product.discountStartAt,
            product.discountEndAt,
            product.currentImages.map { it.imageType to it.storageKey },
        )

    private fun appendVersion(
        product: Product,
        versionNo: Int,
    ) {
        try {
            productVersionRepository.save(
                ProductVersion.snapshot(
                    product = product,
                    versionNo = versionNo,
                    nameSnapshot = product.name,
                    priceSnapshot = product.regularPrice,
                    statusSnapshot = product.saleStatus,
                    // 상품 쪽은 대표 이미지가 없을 수 있는데 스냅샷 컬럼은 NOT NULL이라 그때는 빈 문자열을 넣음.
                    thumbnailKeySnapshot = product.representativeImageKey.orEmpty(),
                    changedBy = VersionChangedBy.SELLER,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (ProductVersion.UK_PRODUCT_VERSION_NO in e.mostSpecificCause.message.orEmpty()) {
                throw BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION, e)
            }
            throw e
        }
    }

    /** 옵션값 id로 조합 서명을 만들어야 해서 그룹을 먼저 저장해 id를 받는다. */
    private fun registerOptions(
        product: Product,
        options: ProductOptions,
    ) {
        val groups =
            optionGroupRepository.saveAll(
                options.groups.mapIndexed { index, group ->
                    OptionGroup.create(product, group.name, index, group.valueNames)
                },
            )
        val valuesByName = groups.flatMap { it.values }.associateBy { it.name }
        optionCombinationRepository.saveAll(
            options.combinations.map { combination ->
                val optionValues = combination.valueNames.map { valuesByName.getValue(it) }
                OptionCombination.create(
                    product = product,
                    name =
                        optionValues
                            .joinToString(COMBINATION_NAME_SEPARATOR) { it.name }
                            .ifEmpty { DEFAULT_COMBINATION_NAME }, // optionValue name이 없으면 기본으로
                    additionalPrice = combination.additionalPrice,
                    stockQuantity = combination.stockQuantity,
                    valueSignature = optionValues.map { it.id }.sorted().joinToString(SIGNATURE_SEPARATOR),
                    optionValues = optionValues,
                )
            },
        )
    }

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

    private fun loadLeafCategory(categoryId: Long): Category {
        val category =
            categoryRepository.findActiveById(categoryId)
                ?: throw BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND)
        if (category.depth != Category.LEAF_DEPTH) {
            throw BusinessException(ProductErrorCode.INVALID_CATEGORY_DEPTH)
        }
        return category
    }

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
            productCatalogQueryPort.findIdByPublicId(decoded.publicId)
                ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return CursorAnchor(lastId = lastId, lastPrice = decoded.lastPrice, anchorNow = decoded.anchorNow)
    }

    private data class CursorAnchor(
        val lastId: Long,
        val lastPrice: Long?,
        val anchorNow: LocalDateTime?,
    )

    companion object {
        private const val COMBINATION_NAME_SEPARATOR = " / "
        private const val SIGNATURE_SEPARATOR = ","

        private const val DEFAULT_COMBINATION_NAME = "기본"
    }
}
