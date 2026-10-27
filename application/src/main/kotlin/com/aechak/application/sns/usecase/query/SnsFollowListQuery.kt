package com.aechak.application.sns.usecase.query

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.support.CursorPageSize
import java.text.Normalizer

/** 팔로워, 팔로잉 목록 입력. viewerId는 principal이다 */
data class SnsFollowListQuery(
    val viewerId: Long,
    val ownerPublicId: String,
    val direction: SnsFollowDirection,
    val cursor: String?,
    val size: Int = CursorPageSize.DEFAULT,
    val rawNickname: String? = null,
) {
    /** trim과 NFC 정규화한 검색어. 비면 null이라 거르지 않는다 */
    val nickname: String? = rawNickname?.let { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }?.ifEmpty { null }

    /** 저장된 닉네임 최대 길이를 넘는 검색어는 아무와도 맞지 않는다 */
    val nicknameTooLong: Boolean = nickname != null && nickname.codePointCount(0, nickname.length) > NICKNAME_MAX

    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }

    companion object {
        private const val NICKNAME_MAX = 12
    }
}
