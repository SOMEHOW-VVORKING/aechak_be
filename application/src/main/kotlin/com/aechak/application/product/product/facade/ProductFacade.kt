package com.aechak.application.product.product.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.product.like.service.ProductLikeStatusService
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
import com.aechak.application.product.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.product.usecase.result.ProductResult
import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.aechak.application.product.product.usecase.result.ProductUpdateResult
import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
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
        val version = tx.execute { productService.register(stored, options) }!!
        return ProductRegisterResult.of(version.product, version.versionNo)
    }

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
