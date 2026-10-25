package com.aechak.application.product.product.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.inspection.service.InspectionAppealService
import com.aechak.application.inspection.service.InspectionQueryService
import com.aechak.application.inspection.service.InspectionRequestService
import com.aechak.application.inspection.usecase.command.RequestProductInspectionCommand
import com.aechak.application.product.like.service.ProductLikeStatusService
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.product.service.ProductInspectionGateService
import com.aechak.application.product.product.service.ProductService
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.AppealProductInspectionCommand
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
import com.aechak.application.product.product.usecase.result.SellerProductInspectionResult
import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.option.ProductOptions
import com.aechak.domain.product.product.Product
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
    private val inspectionQueryService: InspectionQueryService,
    private val inspectionAppealService: InspectionAppealService,
    private val productInspectionGateService: ProductInspectionGateService,
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
                val requested =
                    requestInspection(
                        productId = saved.product.id,
                        versionNo = saved.versionNo,
                        categoryId = stored.categoryId,
                        productName = stored.productName,
                        description = stored.description,
                        optionNames = optionNamesOf(options),
                        representativeImageKey = saved.product.representativeImageKey,
                    )
                // 요청이 나가지 않았으면 판정도 오지 않으므로 숨기지 않음. 숨기면 영영 PENDING에 남음
                if (requested) productInspectionGateService.holdUntilInspected(saved.product)
                saved
            }!!
        return ProductRegisterResult.of(version.product, version.versionNo)
    }

    /**
     * 수정도 등록과 같은 검수를 탄다.
     *
     * 붙이지 않으면 정상 문구로 등록해 통과 판정을 받은 뒤 위반 문구로 고치는 것만으로
     * 검수를 통째로 건너뛸 수 있다. 노출 게이트를 켜는 순간 그 구멍이 게이트를 장식으로 만든다.
     *
     * 옵션 이름을 저장된 값에서 읽어 오는 이유는 수정 명령에 옵션이 들어 있지 않기 때문이다.
     * 내용 해시가 상품명·설명·옵션 이름으로 계산되므로, 옵션을 빼면 본문이 하나도 바뀌지 않은
     * 상품도 등록 때와 다른 해시를 받아 모델을 다시 부른다.
     */
    override fun updateProduct(command: UpdateProductCommand): ProductUpdateResult {
        requireActiveSeller(command.sellerId)
        val storedKeys = tx.execute { productService.validateAndGetCurrentImageKeys(command) }!!
        val stored = promoteNewImageKeys(command, storedKeys)
        val outcome =
            tx.execute {
                val updated = productService.updateProduct(stored)
                // 버전이 남지 않았다는 것은 수정 가능한 필드가 하나도 달라지지 않았다는 뜻이다.
                // 판정 대상 본문도 그대로이므로 요청하지 않는다.
                updated.versionNo?.let { versionNo ->
                    requestInspection(
                        productId = updated.product.id,
                        versionNo = versionNo,
                        categoryId = stored.categoryId,
                        productName = stored.productName,
                        description = stored.description,
                        optionNames = productService.optionNamesOf(updated.product.id),
                        representativeImageKey = updated.product.representativeImageKey,
                    )
                }
                updated
            }!!
        return ProductUpdateResult.of(outcome.product)
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

    @Transactional(readOnly = true)
    override fun getMyProductInspection(
        sellerId: Long,
        publicId: String,
    ): SellerProductInspectionResult {
        requireProductReadableSeller(sellerId)
        return inspectionResultOf(productService.getOwnedProduct(sellerId, publicId))
    }

    /**
     * 자격 게이트와 소유권 검사를 조회와 같은 부품으로 한다 — 남의 상품은 없는 것으로 답한다.
     *
     * 쓰기 자체는 검수 쪽 서비스가 한다. 셀러 실행 모듈은 검수의 service 패키지만 스캔하므로
     * 파사드에 쓰기를 두면 그 경로가 셀러센터에 아예 없다.
     */
    @Transactional
    override fun appealMyProductInspection(command: AppealProductInspectionCommand): SellerProductInspectionResult {
        requireProductReadableSeller(command.sellerId)
        val product = productService.getOwnedProduct(command.sellerId, command.productPublicId)
        inspectionAppealService.submit(command.sellerId, product.id, command.reason)
        return inspectionResultOf(product)
    }

    private fun inspectionResultOf(product: Product): SellerProductInspectionResult =
        SellerProductInspectionResult.of(
            product = product,
            snapshot = inspectionQueryService.findLatestProductInspection(product.id),
            openReview = inspectionQueryService.findOpenProductReview(product.id),
            latestRejectReason = inspectionQueryService.findLatestRejectReason(product.id),
        )

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
     *
     * @return 요청 발행 스위치가 꺼져 있으면 false
     */
    private fun requestInspection(
        productId: Long,
        versionNo: Int,
        categoryId: Long,
        productName: String,
        description: String?,
        optionNames: List<String>,
        representativeImageKey: String?,
    ): Boolean =
        inspectionRequestService.requestProduct(
            RequestProductInspectionCommand(
                productId = productId,
                productVersionNo = versionNo,
                productName = productName,
                description = description,
                optionNames = optionNames,
                categoryPath = productService.categoryPathOf(categoryId),
                imageUrls = listOfNotNull(fileUseCase.resolveMediaUrl(representativeImageKey)),
            ),
        ) != null

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
