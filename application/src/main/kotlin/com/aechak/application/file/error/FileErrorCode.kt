package com.aechak.application.file.error

import com.aechak.common.error.ErrorCode

enum class FileErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    UNSUPPORTED_FILE_TYPE(100000, "지원하지 않는 파일 형식입니다.", 400),
    FILE_NOT_FOUND(100001, "업로드된 파일을 찾을 수 없습니다.", 404),
    FILE_ACCESS_DENIED(100002, "본인이 업로드한 파일만 사용할 수 있습니다.", 403),
    FILE_PURPOSE_MISMATCH(100003, "발급 용도와 다른 파일은 사용할 수 없습니다.", 400),
}
