package com.aechak.application.inspection.usecase.command

/**
 * 상품 검수 요청.
 *
 * 판정에 필요한 본문을 호출부가 전부 채워 넣는다. AI 서버가 우리를 되묻지 않게 하려는 것이고,
 * 그래야 역방향 호출 인증과 보안그룹 인바운드가 필요 없어진다.
 *
 * `imageUrls`는 키가 아니라 공개 주소 완성형이다. 키만 보내면 조립 규칙을 저쪽이 중복으로
 * 들고 있어야 하고, 그 규칙이 어긋나면 조용히 빈 이미지를 판정한다.
 */
data class RequestProductInspectionCommand(
    val productId: Long,
    val productVersionNo: Int,
    val productName: String,
    val description: String?,
    val optionNames: List<String>,
    val categoryPath: List<String>,
    val imageUrls: List<String>,
)

/**
 * 리뷰 검수 요청.
 *
 * `content`는 노출용 본문이어야 한다. 원문을 보내면 이미 마스킹한 욕설이 판정 결과의
 * 인용을 통해 다시 드러난다.
 */
data class RequestReviewInspectionCommand(
    val reviewId: Long,
    val productId: Long,
    val content: String,
    val rating: Int,
    val categoryPath: List<String>,
)
