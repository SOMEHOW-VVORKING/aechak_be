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
}
