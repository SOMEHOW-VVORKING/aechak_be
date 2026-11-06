package com.aechak.domain.sns.stats

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "sns_post_stats")
class SnsPostStats protected constructor(
    @Id
    val postId: Long,
) : AggregateRoot() {
    // 수는 저장소 원자 갱신으로만 바꾼다
    var repostCount: Int = 0
        protected set
}
