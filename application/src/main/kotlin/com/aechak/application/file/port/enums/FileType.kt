package com.aechak.application.file.port.enums

/**
 * 업로드 허용 파일 타입
 * HEIC는 브라우저 렌더링이 이슈로 클라이언트가 JPEG 등으로 변환해 업로드해야 함.
 */
enum class FileType(
    val mimeType: String,
    val extension: String,
) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),

    PDF("application/pdf", "pdf"),
    ;

    companion object {
        fun from(mimeType: String): FileType? = entries.find { it.mimeType == mimeType }
    }
}
