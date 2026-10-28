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
import com.aechak.application.product.product.usecase.result.SellerProductStatusCountsResult
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
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
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
    transactionManager: PlatformTransactionManager,
) : ProductUseCase,
    SellerProductUseCase {
    private val tx = TransactionTemplate(transactionManager)

    /**
     * 상품 수정의 쓰기 경계. **READ COMMITTED로 연다.**
     *
     * 이 트랜잭션 안에서 이의 무효화가 돈다. 무효화는 간극 잠금을 피하려고 열린 검토 행을
     * 비잠금으로 먼저 걸러내는데, 기본 격리(REPEATABLE READ)에서는 읽기 뷰가 상품 조회(이
     * 트랜잭션의 첫 일관 읽기)에서 고정된다. 그러면 그 사이 커밋된 이의가 보이지 않아 무효화가
     * 조용히 건너뛰고, 셀러가 적은 사유는 이미 없어진 내용을 가리키는데 재접수는 중복으로
     * 막혀 셀러가 할 수 있는 것이 없어진다. 셀러가 이의를 넣고 곧바로 문구를 고치면(두 탭·
     * 중복 클릭·재시도) 두 요청이 겹친다.
     *
     * 행이 없을 때 이어지는 잠금 조회가 비유일 인덱스의 꼬리 간극을 쥐지 않는 것도 같은 선언이
     * 떠받친다. 그 간극이 판정 경로의 첫 큐 등재 INSERT 자리다.
     */
    private val writeTx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

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
                inspectionRequestService.requestProduct(
                    productInspectionCommand(
                        productId = saved.product.id,
                        versionNo = saved.versionNo,
                        categoryId = stored.categoryId,
                        productName = stored.productName,
                        description = stored.description,
                        optionNames = optionNamesOf(options),
                        representativeImageKey = saved.product.representativeImageKey,
                    ),
                )
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
            writeTx.execute {
                val updated = productService.updateProduct(stored)
                // 버전이 남지 않았다는 것은 수정 가능한 필드가 하나도 달라지지 않았다는 뜻이다.
                // 판정 대상 본문도 그대로이므로 요청하지 않는다.
                updated.versionNo?.let { versionNo ->
                    val inspectionCommand =
                        productInspectionCommand(
                            productId = updated.product.id,
                            versionNo = versionNo,
                            categoryId = stored.categoryId,
                            productName = stored.productName,
                            description = stored.description,
                            optionNames = productService.optionNamesOf(updated.product.id),
                            representativeImageKey = updated.product.representativeImageKey,
                        )
                    inspectionRequestService.requestProduct(inspectionCommand)
                    // 내용이 바뀌면 그 전 내용에 걸린 이의는 지금 걸린 것을 가리키지 않는다.
                    // 판정이 와서 비워 주기를 기다릴 수 없다 — 판정이 실패하거나 기한을 넘기면
                    // 그 판정은 검토 큐에 닿지 않아 비울 기회가 아예 없다.
                    //
                    // 지문을 요청 발행에서 받지 않고 따로 계산한다. 발행 스위치가 꺼지면 요청은
                    // null을 돌려주는데, 장애 때 요청을 끄는 것이 그 스위치의 용도라서 그 시점에
                    // 열린 이의가 이미 있다. 거기에 무효화까지 묶이면 셀러가 문구를 고쳐도
                    // 이의가 그대로 남는다.
                    inspectionAppealService.invalidateOnContentChange(
                        updated.product.id,
                        inspectionRequestService.productContentHash(inspectionCommand),
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

    /** 자격 게이트 없음 — 계약 KDoc대로 호출자(대시보드 조립)가 이미 통과시킨 sellerId만 들어온다. */
    @Transactional(readOnly = true)
    override fun getMyProductStatusCounts(sellerId: Long): SellerProductStatusCountsResult =
        SellerProductStatusCountsResult.from(productService.getSellerStatusCounts(sellerId))

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
     *
     * READ_COMMITTED: 상품 행 잠금 전에 자격 게이트와 소유권 조회가 돌아 REPEATABLE READ
     * 스냅샷이 먼저 고정된다. 그러면 잠금을 받은 뒤의 정책 조회(열린 이의 건수·기각 이력)가
     * 그 사이 커밋된 접수를 못 보고 상한과 쿨다운이 같은 분모를 두 번 쓴다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun appealMyProductInspection(command: AppealProductInspectionCommand): SellerProductInspectionResult {
        requireProductReadableSeller(command.sellerId)
        val product = productService.getOwnedProduct(command.sellerId, command.productPublicId)
        inspectionAppealService.submit(command.sellerId, product.id, command.reason)
        return inspectionResultOf(product)
    }

    private fun inspectionResultOf(product: Product): SellerProductInspectionResult {
        val openReview = inspectionQueryService.findOpenProductReview(product.id)
        return SellerProductInspectionResult.of(
            product = product,
            snapshot = inspectionQueryService.findLatestProductInspection(product.id),
            openReview = openReview,
            // 사유는 지금 그 행을 막고 있는 판정에서 읽는다. 가장 최근 검수에서 읽으면 모델이
            // 답을 못 준 뒤에 반려만 남고 고칠 곳을 알려 주는 문구가 사라진다.
            blocking =
                openReview?.let {
                    inspectionQueryService.findBlockingProductInspection(product.id, it.blindedAt)
                },
            adminRejectReason = inspectionQueryService.findAdminRejectReason(product.id),
        )
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
     * 검수 요청 한 건. 판정에 필요한 본문을 여기서 다 모은다.
     *
     * AI 서버가 우리를 되묻지 않게 하려는 것이고, 그래야 역방향 호출 인증과 보안그룹 인바운드가
     * 필요 없어진다.
     *
     * 커맨드로 돌려주는 이유는 내용 지문이다. 수정 경로는 요청 발행과 이의 무효화 둘에 같은
     * 본문이 필요한데, 발행 스위치가 꺼지면 요청이 null을 돌려줘서 그쪽에서 지문을 받을 수 없다.
     */
    private fun productInspectionCommand(
        productId: Long,
        versionNo: Int,
        categoryId: Long,
        productName: String,
        description: String?,
        optionNames: List<String>,
        representativeImageKey: String?,
    ): RequestProductInspectionCommand =
        RequestProductInspectionCommand(
            productId = productId,
            productVersionNo = versionNo,
            productName = productName,
            description = description,
            optionNames = optionNames,
            categoryPath = productService.categoryPathOf(categoryId),
            imageUrls = listOfNotNull(fileUseCase.resolveMediaUrl(representativeImageKey)),
        )

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
