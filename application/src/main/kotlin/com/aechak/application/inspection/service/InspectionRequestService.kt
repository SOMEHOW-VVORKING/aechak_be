package com.aechak.application.inspection.service

import com.aechak.application.inspection.support.ContentHash
import com.aechak.application.inspection.usecase.command.RequestProductInspectionCommand
import com.aechak.application.inspection.usecase.command.RequestReviewInspectionCommand
import com.aechak.application.messaging.MessagePublisher
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.message.inspection.ProductInspectionRequestedMessage
import com.aechak.message.inspection.ReviewInspectionRequestedMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 검수 요청을 적고 발행한다.
 *
 * 트랜잭션 애너테이션이 없다. 이 서비스는 **호출부의 트랜잭션 안에서** 돌아야 하기 때문이다.
 * 유실 불가 메시지는 같은 트랜잭션에 아웃박스 행을 함께 적어야 하고, 별 트랜잭션을 열면
 * 상품 저장은 롤백되는데 검수 요청만 나가는 어긋남이 생긴다.
 */
@Service
class InspectionRequestService(
    private val inspections: ContentInspectionRepository,
    private val publisher: MessagePublisher,
    @Value("\${inspection.policy-version:policy-v1}") private val policyVersion: String,
    /**
     * 검수 요청 발행 스위치. 기본은 꺼짐이다.
     *
     * 꺼져 있으면 아무것도 적지 않고 아무것도 보내지 않는다. 켤 준비가 된 환경에서만
     * 켜는 것이 목적이고, 덕분에 되돌릴 때 코드를 손대지 않는다.
     */
    @Value("\${inspection.enabled:false}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 상품 검수를 요청한다. 같은 내용을 같은 정책으로 이미 봤으면 그 판정을 재사용한다.
     *
     * @return 이번에 새로 요청했으면 그 검수, 재사용이면 기존 검수, 스위치가 꺼져 있으면 null
     */
    fun requestProduct(command: RequestProductInspectionCommand): ContentInspection? {
        if (!enabled) return null
        requireActiveTransaction()

        val contentHash = productContentHash(command)
        val existing =
            inspections.findByTarget(
                InspectionTargetType.PRODUCT,
                command.productId,
                contentHash,
                policyVersion,
            )
        if (existing != null) {
            log.debug(
                "같은 내용의 상품 검수를 재사용한다. productId={} inspectionId={}",
                command.productId,
                existing.id,
            )
            return existing
        }

        val saved =
            inspections.save(
                ContentInspection.queue(
                    targetType = InspectionTargetType.PRODUCT,
                    targetRef = command.productId,
                    contentHash = contentHash,
                    policyVersion = policyVersion,
                    targetVersionNo = command.productVersionNo,
                ),
            )
        publisher.publish(
            ProductInspectionRequestedMessage(
                inspectionId = saved.id,
                productId = command.productId,
                productVersionNo = command.productVersionNo,
                contentHash = contentHash,
                productName = command.productName,
                description = command.description,
                optionNames = command.optionNames,
                categoryPath = command.categoryPath,
                imageUrls = command.imageUrls,
            ),
        )
        return saved
    }

    /**
     * 판정 대상 본문의 지문. **요청 발행 스위치를 보지 않는다.**
     *
     * 요청을 끈 환경에서도 지문이 필요하다. 셀러가 내용을 고치면 그 전 내용에 걸린 이의를
     * 무효로 돌려야 하는데, 그 판단의 축이 이 지문이다. 요청 발행에서 지문을 함께 받아 쓰면
     * 스위치를 끄는 순간 무효화까지 함께 꺼지고, 장애 때 요청을 끄는 것이 바로 그 스위치의
     * 용도라서 셀러가 문구를 고쳐도 이의가 그대로 남는다.
     *
     * 한 자리에 두는 이유는 조성 때문이다. 어느 필드가 지문에 들어가는지가 두 곳에 있으면
     * 무효화가 판정이 들고 올 지문과 다른 축에서 비교된다.
     */
    fun productContentHash(command: RequestProductInspectionCommand): String =
        ContentHash.of(
            listOf(command.productName, command.description) + command.optionNames,
        )

    /** 리뷰 검수를 요청한다. 차단된 리뷰는 호출부가 아예 부르지 않는다 */
    fun requestReview(command: RequestReviewInspectionCommand): ContentInspection? {
        if (!enabled) return null
        requireActiveTransaction()

        val contentHash = ContentHash.of(listOf(command.content, command.rating.toString()))
        val existing =
            inspections.findByTarget(
                InspectionTargetType.REVIEW,
                command.reviewId,
                contentHash,
                policyVersion,
            )
        if (existing != null) return existing

        val saved =
            inspections.save(
                ContentInspection.queue(
                    targetType = InspectionTargetType.REVIEW,
                    targetRef = command.reviewId,
                    contentHash = contentHash,
                    policyVersion = policyVersion,
                ),
            )
        publisher.publish(
            ReviewInspectionRequestedMessage(
                inspectionId = saved.id,
                reviewId = command.reviewId,
                productId = command.productId,
                contentHash = contentHash,
                content = command.content,
                rating = command.rating,
                categoryPath = command.categoryPath,
            ),
        )
        return saved
    }

    private fun requireActiveTransaction() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "검수 요청은 호출부의 트랜잭션 안에서만 발행할 수 있다"
        }
    }
}
