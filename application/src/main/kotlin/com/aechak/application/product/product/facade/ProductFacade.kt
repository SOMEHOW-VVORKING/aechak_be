package com.aechak.application.product.product.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.inspection.service.InspectionRequestService
import com.aechak.application.inspection.usecase.command.RequestProductInspectionCommand
import com.aechak.application.product.like.service.ProductLikeStatusService
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.product.service.ProductService
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.ChangeOptionCombinationCommand
import com.aechak.application.product.product.usecase.command.ChangeProductSaleStatusCommand
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.product.usecase.query.SellerProductSearchQuery
import com.aechak.application.product.product.usecase.result.OptionCombinationChangeResult
import com.aechak.application.product.product.usecase.result.ProductCurationResult
import com.aechak.application.product.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.product.usecase.result.ProductResult
import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.aechak.application.product.product.usecase.result.ProductUpdateResult
import com.aechak.application.product.product.usecase.result.SellerProductDetailResult
import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.option.ProductOptions
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class ProductFacade(
    private val productService: ProductService,
    private val productStatsService: ProductStatsService,
    private val productLikeStatusService: ProductLikeStatusService,
    private val sellerUseCase: SellerUseCase,
    private val fileUseCase: FileUseCase,
    private val inspectionRequestService: InspectionRequestService,
    transactionManager: PlatformTransactionManager,
) : ProductUseCase,
    SellerProductUseCase {
    private val tx = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    override fun getProducts(
        query: ProductSearchQuery,
        userId: Long?,
    ): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productService.getVisiblePage(query, now)
        val likedIds = productLikeStatusService.likedProductIds(userId, page.items.map { it.id })
        return ProductSummaryResult.fromPage(page, likedIds, now)
    }

    @Transactional(readOnly = true)
    override fun getCuration(userId: Long?): ProductCurationResult {
        val now = LocalDateTime.now()
        val ranking = productService.getPopular(now)
        val recommended = productService.getRandomOnSale(now)
        val likedIds =
            productLikeStatusService.likedProductIds(userId, (ranking + recommended).map { it.id }.distinct())

        fun cards(views: List<ProductCatalogView>) = views.map { ProductSummaryResult.from(it, now, it.id in likedIds) }

        return ProductCurationResult(recommended = cards(recommended), ranking = cards(ranking))
    }

    @Transactional(readOnly = true)
    override fun getProduct(
        publicId: String,
        userId: Long?,
    ): ProductResult {
        val now = LocalDateTime.now()
        val detail = productService.getVisibleDetail(publicId)
        val images = productService.getImages(detail.id)
        val stats = productStatsService.getStatsByProductIds(listOf(detail.id))[detail.id]
        val isLiked = userId?.let { productService.isLiked(detail.id, it) } ?: false
        return ProductResult.from(view = detail, images = images, stats = stats, isLiked = isLiked, now = now)
    }

    @Transactional(readOnly = true)
    override fun getProductOptions(publicId: String): ProductOptionsResult =
        ProductOptionsResult.from(productService.getVisibleOptions(publicId))

    @Transactional(readOnly = true)
    override fun getVisibleProductId(publicId: String): Long = productService.getVisibleProductId(publicId)

    override fun registerProduct(command: RegisterProductCommand): ProductRegisterResult {
        requireActiveSeller(command.sellerId)
        val options = command.toProductOptions()
        val stored = promoteNewImageKeys(command)
        // 검수 요청을 같은 트랜잭션에서 발행한다. 상품 저장이 롤백되면 요청도 함께 사라져야 한다.
        val version =
            tx.execute {
                val saved = productService.register(stored, options)
                requestInspection(stored.categoryId, stored.productName, stored.description, options, saved)
                saved
            }!!
        return ProductRegisterResult.of(version.product, version.versionNo)
    }

    /**
     * 수정은 아직 검수를 다시 타지 않는다.
     *
     * 지금은 섀도 단계라 노출 상태를 바꾸지 않으므로 우회할 대상이 없다. 다만 **노출 게이트를
     * 켜기 전에 수정 재검수를 반드시 붙여야 한다** — 정상 문구로 등록한 뒤 위반 문구로 고치면
     * 검수를 통째로 우회할 수 있고, 그러면 게이트가 장식이 된다.
     */
    override fun updateProduct(command: UpdateProductCommand): ProductUpdateResult {
        requireActiveSeller(command.sellerId)
        val storedKeys = tx.execute { productService.validateAndGetCurrentImageKeys(command) }!!
        val stored = promoteNewImageKeys(command, storedKeys)
        return tx.execute { productService.updateProduct(stored) }!!
    }

    @Transactional
    override fun changeProductSaleStatus(command: ChangeProductSaleStatusCommand): ProductSaleStatusChangeResult {
        requireActiveSeller(command.sellerId)
        return productService.changeSaleStatus(command)
    }

    @Transactional
    override fun changeOptionCombination(command: ChangeOptionCombinationCommand): OptionCombinationChangeResult {
        requireActiveSeller(command.sellerId)
        return productService.changeOptionCombination(command)
    }

    @Transactional(readOnly = true)
    override fun getMyProducts(query: SellerProductSearchQuery): OffsetPageResult<SellerProductSummaryResult> {
        requireProductReadableSeller(query.sellerId)
        val now = LocalDateTime.now()
        return productService.getSellerPage(query, now).map { SellerProductSummaryResult.from(view = it, now = now) }
    }

    @Transactional(readOnly = true)
    override fun getMyProductDetail(
        sellerId: Long,
        publicId: String,
    ): SellerProductDetailResult {
        requireActiveSeller(sellerId)
        return SellerProductDetailResult.from(productService.getOwnedProduct(sellerId, publicId))
    }

    @Transactional(readOnly = true)
    override fun getMyProductOptions(
        sellerId: Long,
        publicId: String,
    ): SellerProductOptionsResult {
        requireProductReadableSeller(sellerId)
        return SellerProductOptionsResult.from(productService.getOwnedOptions(sellerId, publicId))
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
    }

    /** 조회 자격 게이트 — 자격 소멸(탈퇴)과 제재(정지)만 막는다. 휴점·탈퇴신청 셀러는 자기 상품을 본다 */
    private fun requireProductReadableSeller(sellerId: Long) {
        if (sellerUseCase.getSellerStatus(sellerId) !in PRODUCT_READABLE_SELLER_STATUSES) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_READ_FORBIDDEN)
        }
    }

    /**
     * 검수 요청을 발행한다. 호출부의 트랜잭션 안에서 돌아야 한다.
     *
     * 판정에 필요한 본문을 여기서 다 모아 보낸다. AI 서버가 우리를 되묻지 않게 하려는 것이고,
     * 그래야 역방향 호출 인증과 보안그룹 인바운드가 필요 없어진다.
     */
    private fun requestInspection(
        categoryId: Long,
        productName: String,
        description: String?,
        options: ProductOptions,
        version: ProductVersion,
    ) {
        inspectionRequestService.requestProduct(
            RequestProductInspectionCommand(
                productId = version.product.id,
                productVersionNo = version.versionNo,
                productName = productName,
                description = description,
                optionNames = optionNamesOf(options),
                categoryPath = productService.categoryPathOf(categoryId),
                imageUrls = listOfNotNull(fileUseCase.resolveMediaUrl(version.product.representativeImageKey)),
            ),
        )
    }

    /** 그룹명과 값 이름을 함께 넘긴다. 옵션 이름에도 금칙 표현이 들어온다 */
    private fun optionNamesOf(options: ProductOptions): List<String> =
        options.groups.flatMap { group -> listOf(group.name) + group.valueNames }

    private fun promoteNewImageKeys(command: RegisterProductCommand): RegisterProductCommand =
        command.copy(
            thumbnailImageKey = promoteToStorageKey(command.sellerId, command.thumbnailImageKey),
            additionalImageKeys = command.additionalImageKeys.map { promoteToStorageKey(command.sellerId, it) },
            detailImageKeys = command.detailImageKeys.map { promoteToStorageKey(command.sellerId, it) },
        )

    /**
     * 승격된 키에는 소유자 정보가 없어 파일 쪽이 소유를 못 가림.
     * 이 상품에 붙어 있던 값과 같은 것만이 소유 증명이고, 나머지는 전부 새 업로드로 봄.
     */
    private fun promoteNewImageKeys(
        command: UpdateProductCommand,
        storedKeys: Set<String>,
    ): UpdateProductCommand =
        command.copy(
            thumbnailImageKey = promoteUnlessStored(command.sellerId, command.thumbnailImageKey, storedKeys),
            additionalImageKeys =
                command.additionalImageKeys.map {
                    promoteUnlessStored(
                        command.sellerId,
                        it,
                        storedKeys,
                    )
                },
            detailImageKeys = command.detailImageKeys.map { promoteUnlessStored(command.sellerId, it, storedKeys) },
        )

    private fun promoteUnlessStored(
        sellerId: Long,
        key: String,
        storedKeys: Set<String>,
    ): String = if (key in storedKeys) key else promoteToStorageKey(sellerId, key)

    private fun promoteToStorageKey(
        sellerId: Long,
        tmpKey: String,
    ): String = fileUseCase.promote(PromoteFileCommand(tmpKey, sellerId, UploadPurpose.PRODUCT)).key

    companion object {
        private val PRODUCT_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)
    }
}
