package com.aechak.application.product.draft.facade

import com.aechak.application.product.draft.port.ProductDraftAutofillPort
import com.aechak.application.product.draft.port.ProductDraftAutofillRequest
import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import com.aechak.application.product.draft.port.ProductDraftAutofillThrottle
import com.aechak.application.product.draft.usecase.ProductDraftAutofillUseCase
import com.aechak.application.product.draft.usecase.command.AutofillProductDraftCommand
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * 초안 받기 유스케이스 구현. 아무것도 저장하지 않는다 — 셀러가 값을 받아들이면 기존 임시저장
 * 버튼이 저장한다.
 *
 * 트랜잭션을 열지 않는다. AI 호출이 최악 30초라 그 안에 넣으면 커넥션을 그만큼 잡고, DB 를
 * 보는 것은 자격 확인 한 번뿐인데 그 경계는 SellerFacade 가 이미 들고 있다.
 *
 * autofillPort 는 선택 주입이다 — ApiApplication 이 루트를 필터 없이 스캔해 이 파사드를 api 도
 * 조립하는데 :api 에는 :ai-client 가 없다. throttle 은 비널이다(api·seller 둘 다 :redis 보유).
 */
@Service
class ProductDraftAutofillFacade(
    private val sellerUseCase: SellerUseCase,
    private val autofillPort: ProductDraftAutofillPort?,
    private val throttle: ProductDraftAutofillThrottle,
    @Value("\${product.draft.autofill.cooldown:20s}") private val cooldown: Duration,
) : ProductDraftAutofillUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun autofill(command: AutofillProductDraftCommand): ProductDraftAutofillResult {
        requireActiveSeller(command.sellerId)

        // 어댑터가 없으면 쿨다운을 태우지 않는다 — 주소·비밀이 등재된 뒤에도 20초를 기다리게 된다
        val port =
            autofillPort ?: run {
                log.error("AI 초안 어댑터가 조립되지 않아 건너뜀. sellerId={}", command.sellerId)
                return ProductDraftAutofillResult.unavailable()
            }
        if (!throttle.tryAcquire(command.sellerId, cooldown)) {
            throw BusinessException(ProductErrorCode.PRODUCT_DRAFT_AUTOFILL_COOLDOWN)
        }

        val requestId = UUID.randomUUID().toString()
        log.info("AI 초안 요청. requestId={}, sellerId={}, categoryId={}", requestId, command.sellerId, command.categoryId)
        return port.generate(
            ProductDraftAutofillRequest(
                requestId = requestId,
                productName = command.productName,
                categoryId = command.categoryId,
                lockedFields = toLockedFields(command),
            ),
        )
    }

    private fun toLockedFields(command: AutofillProductDraftCommand): ProductDraftAutofillRequest.LockedFields? {
        if (command.lockedDescription == null && command.lockedOptionGroups == null) {
            return null
        }
        return ProductDraftAutofillRequest.LockedFields(
            description = command.lockedDescription,
            optionGroups =
                command.lockedOptionGroups?.map {
                    ProductDraftAutofillRequest.OptionGroup(it.name, it.values)
                },
        )
    }

    /** 셀러가 아니면 seller BC 가 10004 로 거른다 — 여기 코드는 등록된 셀러의 상태 정책만 가린다 */
    private fun requireActiveSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) != SellerStatus.ACTIVE) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
    }
}
