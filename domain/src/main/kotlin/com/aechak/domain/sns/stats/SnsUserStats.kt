package com.aechak.domain.sns.stats

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "sns_user_stats")
class SnsUserStats protected constructor(
    @Id
    val userId: Long,
) : AggregateRoot() {
    // 수는 저장소 원자 갱신으로만 바꾼다
    var followerCount: Int = 0
        protected set

    var followingCount: Int = 0
        protected set

    var postCount: Int = 0
        protected set
}
