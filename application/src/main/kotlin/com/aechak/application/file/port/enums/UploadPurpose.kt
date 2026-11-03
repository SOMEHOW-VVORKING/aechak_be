package com.aechak.application.file.port.enums

enum class UploadPurpose(
    val prefix: String,
    val category: StorageCategory,
    val allowedFileTypes: Set<FileType>,
    // 업로드 길이 상한(바이트). 있으면 발급 요청에 길이가 필수이고 그 길이를 PUT 서명에 넣는다
    val maxBytes: Long? = null,
) {
    USER_PROFILE(
        "users/profile",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
    ),
    PET_PROFILE(
        "pets/profile",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
    ),

    PRODUCT(
        "products",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
    ),

    // 셀러 스토어 프로필·배너 이미지 — 구매자에게 보이는 공개 이미지
    SELLER_STORE(
        "sellers/store",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
    ),

    // 입점 심사 서류 — 민감 문서라 공개 미디어 버킷이 아닌 DOCS 소속
    SELLER_DOCUMENT(
        "sellers/documents",
        StorageCategory.DOCS,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP, FileType.PDF),
    ),
    REVIEW_IMAGE(
        "reviews/images",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
    ),
    SNS_POST_IMAGE(
        "sns/posts/images",
        StorageCategory.MEDIA,
        setOf(FileType.PNG, FileType.JPEG, FileType.WEBP),
        maxBytes = 10_485_760,
    ),
}
