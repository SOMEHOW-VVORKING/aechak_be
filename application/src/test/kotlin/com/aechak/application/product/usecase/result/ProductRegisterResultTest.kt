package com.aechak.application.product.usecase.result

import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** 단위 테스트 — 등록 응답이 상품에서 옮겨 싣는 값을 고정한다. 검수 게이트가 없어 등록 직후 판매중, 승인으로 나가는 것도 함께 잡는다. */
class ProductRegisterResultTest {
    @Test
    fun `등록 결과는 상품의 공개 식별자와 생성 시각, 넘겨받은 버전 번호를 싣는다`() {
        val product = registeredProduct()

        val result = ProductRegisterResult.of(product, versionNo = 3)

        assertEquals(product.publicId, result.publicId, "내부 id가 아니라 상품의 공개 식별자를 실어야 한다")
        assertEquals(product.createdAt, result.createdAt, "생성 시각은 상품에서 가져와야 한다")
        assertEquals(3, result.versionNo, "버전 번호는 넘겨받은 값을 그대로 실어야 한다")
    }

    @Test
    fun `등록 직후 상태는 판매중과 검수 승인이다`() {
        val result = ProductRegisterResult.of(registeredProduct(), versionNo = 1)

        assertEquals(SaleStatus.ON_SALE, result.saleStatus, "등록 직후 판매 상태는 판매중이어야 한다")
        assertEquals(InspectionStatus.APPROVED, result.inspectionStatus, "검수 게이트가 없어 등록 직후 승인이어야 한다")
    }

    private fun registeredProduct(): Product {
        val root = category(parent = null, depth = Category.ROOT_DEPTH, name = "반려동물")
        val mid = category(parent = root, depth = Category.MID_DEPTH, name = "사료")
        return Product.register(
            category = category(parent = mid, depth = Category.LEAF_DEPTH, name = "건사료"),
            sellerId = 1L,
            name = "연어 건사료 2kg",
            description = null,
            representativeImageKey = "products/1/thumbnail.png",
            regularPrice = 25_000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
        )
    }

    private fun category(
        parent: Category?,
        depth: Int,
        name: String,
    ): Category = Category.create(parent = parent, depth = depth, name = name, iconUrl = null, sortOrder = 1)
}
