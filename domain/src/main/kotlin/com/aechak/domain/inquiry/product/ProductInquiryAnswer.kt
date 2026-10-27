package com.aechak.domain.inquiry.product

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 셀러 답변. ProductInquiry 애그리거트의 자식이라 루트를 거쳐서만 생기고 지워진다.
 * 답변자를 따로 남기는 이유 — 상품 소유 셀러와 실제 작성자가 갈릴 수 있다(운영 대행·계정 이전).
 *
 * inquiry_id UNIQUE를 매핑에도 선언한다 — 테스트는 ddl-auto=create로 스키마를 만들고 Flyway를 끄므로
 * 마이그레이션에만 두면 "답변은 한 건"을 DB가 받치는 자리가 운영에만 생기고 테스트에는 없다.
 */
@Entity
@Table(
    name = "product_inquiry_answers",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_inquiry_answers_inquiry_id", columnNames = ["inquiry_id"]),
    ],
)
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
