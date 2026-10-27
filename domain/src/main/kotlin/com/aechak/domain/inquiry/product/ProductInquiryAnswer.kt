package com.aechak.domain.inquiry.product

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 셀러 답변. ProductInquiry 애그리거트의 자식이라 루트를 거쳐서만 생기고 지워진다.
 * 답변자를 따로 남기는 이유 — 상품 소유 셀러와 실제 작성자가 갈릴 수 있다(운영 대행·계정 이전).
 */
@Entity
@Table(name = "product_inquiry_answers")
class ProductInquiryAnswer protected constructor(
    val answererUserId: Long,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        fun of(
            answererUserId: Long,
            content: String,
        ): ProductInquiryAnswer = ProductInquiryAnswer(answererUserId, content)
    }
}
