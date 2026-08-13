package com.aechak.application.product.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.product.service.ProductService
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.SellerProductUseCase
import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.usecase.result.ProductResult
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.product.usecase.result.ProductUpdateResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class ProductFacade(
    private val productService: ProductService,
    private val productStatsService: ProductStatsService,
    private val sellerUseCase: SellerUseCase,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : ProductUseCase,
    SellerProductUseCase {
    private val tx = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    override fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productService.getVisiblePage(query, now)
        val statsById = productStatsService.getStatsByProductIds(page.items.map { it.id })
        return CursorPageResult(
            items =
                page.items.map {
                    ProductSummaryResult.from(view = it, stats = statsById[it.id], now = now)
                },
            totalCount = page.totalCount,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
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

    override fun registerProduct(command: RegisterProductCommand): ProductRegisterResult {
        requireActiveSeller(command.sellerId)
        val options = command.toProductOptions()
        val stored = promoteNewImageKeys(command)
        val version = tx.execute { productService.register(stored, options) }!!
        return ProductRegisterResult.of(version.product, version.versionNo)
    }

    override fun updateProduct(command: UpdateProductCommand): ProductUpdateResult {
        requireActiveSeller(command.sellerId)
        val storedKeys = tx.execute { productService.getCurrentImageKeys(command.productPublicId, command.sellerId) }!!
        val stored = promoteNewImageKeys(command, storedKeys)
        return tx.execute { productService.updateProduct(stored) }!!
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
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
}
