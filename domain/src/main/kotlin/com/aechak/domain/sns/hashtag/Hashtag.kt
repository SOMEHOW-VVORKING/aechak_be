package com.aechak.domain.sns.hashtag

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.text.Normalizer
import java.util.Locale

/** SNS 해시태그. 이름은 정규화한 표기이고 게시글 수가 0이 돼도 행을 지우지 않는다 */
@Entity
@Table(
    name = "sns_hashtags",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_hashtags_name", columnNames = ["name"])],
    // post_count DESC, name ASC 정렬을 filesort 없이 읽으려고 post_count만 내림차순으로 둔다
    indexes = [Index(name = "ix_sns_hashtags_post_count", columnList = "post_count desc, name")],
)
class Hashtag protected constructor(
    // 바이트 그대로 비교한다. 기본 콜레이션은 가와 ㄱㅏ를 같은 이름으로 본다
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(30) collate utf8mb4_bin")
    val name: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // 수는 저장소 원자 갱신으로만 바꾼다
    var postCount: Int = 0
        protected set

    companion object {
        private const val NAME_MAX_LENGTH = 30
        private val NAME_PATTERN = Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-z0-9_]+$")

        /** 앞의 # 하나와 앞뒤 공백을 떼고 NFC로 정규화한 뒤 영문을 소문자로 바꾼다. 허용 밖 글자나 빈 값은 130500, 30자 초과는 130501이다 */
        fun normalize(raw: String): String {
            val name = Normalizer.normalize(raw.trim().removePrefix("#").trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
            if (!NAME_PATTERN.matches(name)) throw BusinessException(SnsErrorCode.SNS_HASHTAG_INVALID)
            // 허용 글자가 모두 BMP 한 글자라 length가 코드 포인트 수와 같다
            if (name.length > NAME_MAX_LENGTH) throw BusinessException(SnsErrorCode.SNS_HASHTAG_TOO_LONG)
            return name
        }
    }
}
