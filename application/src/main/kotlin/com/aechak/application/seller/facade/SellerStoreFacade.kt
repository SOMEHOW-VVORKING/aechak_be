package com.aechak.application.seller.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.seller.service.SellerService
import com.aechak.application.seller.service.SellerStoreService
import com.aechak.application.seller.usecase.SellerStoreUseCase
import com.aechak.application.seller.usecase.command.UpdateStoreCommand
import com.aechak.application.seller.usecase.result.StoreSettingsResult
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * SellerStoreUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * updateStore는 TransactionTemplate(프로그램적 경계)을 쓴다 — 이미지 승격이 S3 호출이라
 * 트랜잭션 밖에 둬야 한다: 자격 확인 tx → 승격 → 저장 tx (프로필 이미지 선례).
 */
@Service
class SellerStoreFacade(
    private val sellerService: SellerService,
    private val sellerStoreService: SellerStoreService,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : SellerStoreUseCase {
    private val tx = TransactionTemplate(transactionManager)

    /** 셀러가 아니어도 거절하지 않는다 — 한 화면이 입점 신청과 스토어 운영을 겸하고, 둘의 분기가 이 응답이다. */
    @Transactional(readOnly = true)
    override fun getStore(userId: Long): StoreSettingsResult {
        val seller = sellerService.find(userId)
        return StoreSettingsResult.of(
            seller = seller,
            applicationStatus = sellerStoreService.findApplicationStatus(userId),
            profileImageUrl = fileUseCase.resolveMediaUrl(seller?.profileImageKey),
            bannerImageUrl = fileUseCase.resolveMediaUrl(seller?.bannerImageKey),
        )
    }

    override fun updateStore(command: UpdateStoreCommand) {
        // 승격은 되돌릴 수 없는 외부 쓰기라 셀러 자격을 먼저 본다. 저장 가부 판정은 저장 트랜잭션이 다시 한다.
        val storedKeys = tx.execute { storedImageKeysOf(command.sellerId) }!!
        val promoted =
            command.copy(
                profileImageKey = promoteUnlessStored(command.sellerId, command.profileImageKey, storedKeys),
                bannerImageKey = promoteUnlessStored(command.sellerId, command.bannerImageKey, storedKeys),
            )
        tx.execute { sellerStoreService.applyStore(sellerService.getActive(command.sellerId), promoted) }
    }

    /** 이 스토어에 이미 붙어 있는 이미지 키 — 승격된 키에는 소유자 정보가 없어 이 대조가 유일한 소유 증명이다. */
    private fun storedImageKeysOf(sellerId: Long): Set<String> {
        val seller = sellerService.getActive(sellerId)
        return setOfNotNull(seller.profileImageKey, seller.bannerImageKey)
    }

    /** 저장값과 같은 키는 그대로 유지(재승격 금지), 그 외는 전부 새 업로드로 보고 승격. null은 이미지 제거. */
    private fun promoteUnlessStored(
        sellerId: Long,
        key: String?,
        storedKeys: Set<String>,
    ): String? =
        key?.let {
            if (it in storedKeys) {
                it
            } else {
                fileUseCase.promote(PromoteFileCommand(it, sellerId, UploadPurpose.SELLER_STORE)).key
            }
        }
}
