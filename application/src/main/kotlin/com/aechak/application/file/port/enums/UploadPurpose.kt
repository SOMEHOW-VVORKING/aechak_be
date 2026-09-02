package com.aechak.application.file.port.enums

enum class UploadPurpose(
    val prefix: String,
    val category: StorageCategory,
    val allowedFileTypes: Set<FileType>,
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
}
