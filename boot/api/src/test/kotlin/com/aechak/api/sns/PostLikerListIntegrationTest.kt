package com.aechak.api.sns

import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 통합: 게시물 좋아요한 사람 목록이 최근에 누른 순서로 100명까지 나오고 보는 사람과 차단 관계인 사용자와 탈퇴 사용자를 뺀 뒤 100명을 채우는지.
 * 깨지면 목록 순서가 바뀌거나, 차단 관계인 사용자가 보이거나, 뺀 사용자 때문에 100명이 덜 차거나, likeCount가 보는 사람마다 달라지거나,
 * 행의 펫과 팔로우 여부가 틀리거나, 볼 수 없는 게시물의 좋아요한 사람이 드러난다.
 */
class PostLikerListIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `최근에 누른 순서로 나오고 본인 행은 isMe이며 팔로우 중인 사용자는 following이다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val followed = onboardedUser()
        val other = onboardedUser()
        relate(viewer, followed, "follow")
        listOf(followed, viewer, other).forEach { like(publicId, it).andExpect(status().isCreated) }

        val data = likers(publicId, viewer).andExpect(status().isOk).data()

        assertEquals(3, (data["likeCount"] as Number).toLong())
        assertEquals(false, data["hasMore"])
        val items = data.items()
        assertEquals(listOf(other, viewer, followed).map(::publicIdOf), items.map { it["publicId"] }, "최근에 누른 순서여야 한다")
        assertEquals(listOf(false, true, false), items.map { it["isMe"] })
        assertEquals(listOf(false, false, true), items.map { it["following"] }, "본인 행은 following이 false여야 한다")
        assertEquals(
            setOf("publicId", "nickname", "profileImageUrl", "pet", "isMe", "following"),
            items.first().keys,
            "행에 내부 id가 없어야 한다",
        )
    }

    @Test
    fun `101명이면 최근 100명과 hasMore true이고 100명이면 hasMore false다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val likers = List(101) { onboardedUser() }
        insertLikes(publicId, likers)

        val over = likers(publicId, viewer).andExpect(status().isOk).data()
        assertEquals(101L, (over["likeCount"] as Number).toLong())
        assertEquals(true, over["hasMore"])
        assertEquals(likers.drop(1).reversed().map(::publicIdOf), over.items().map { it["publicId"] }, "가장 먼저 누른 1명이 빠져야 한다")

        unlike(publicId, likers.first()).andExpect(status().isOk)
        val exact = likers(publicId, viewer).andExpect(status().isOk).data()
        assertEquals(100, exact.items().size)
        assertEquals(false, exact["hasMore"])
    }

    @Test
    fun `차단 관계인 사용자는 두 방향 모두 빠지고 likeCount는 그대로이며 뮤트한 사용자는 나온다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val blockedByViewer = onboardedUser()
        val blockingViewer = onboardedUser()
        val muted = onboardedUser()
        listOf(blockedByViewer, blockingViewer, muted).forEach { like(publicId, it).andExpect(status().isCreated) }
        relate(viewer, blockedByViewer, "block")
        relate(blockingViewer, viewer, "block")
        relate(viewer, muted, "mute")

        val data = likers(publicId, viewer).andExpect(status().isOk).data()

        assertEquals(3L, (data["likeCount"] as Number).toLong(), "차단 관계로 뺀 사용자도 likeCount에 들어가야 한다")
        assertEquals(listOf(publicIdOf(muted)), data.items().map { it["publicId"] })
        assertEquals(false, data["hasMore"])
    }

    @Test
    fun `101명 가운데 1명이 차단 관계면 나머지 100명과 hasMore false다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val likers = List(101) { onboardedUser() }
        insertLikes(publicId, likers)
        relate(viewer, likers.last(), "block")

        val data = likers(publicId, viewer).andExpect(status().isOk).data()

        assertEquals(101L, (data["likeCount"] as Number).toLong())
        assertEquals(likers.dropLast(1).reversed().map(::publicIdOf), data.items().map { it["publicId"] }, "뺀 뒤 100명을 채워야 한다")
        assertEquals(false, data["hasMore"])
    }

    @Test
    fun `모두 차단 관계면 빈 items와 전체 likeCount다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val blocked = onboardedUser()
        like(publicId, blocked).andExpect(status().isCreated)
        relate(viewer, blocked, "block")

        likers(publicId, viewer)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(1))
            .andExpect(jsonPath("$.data.hasMore").value(false))
            .andExpect(jsonPath("$.data.items.length()").value(0))
    }

    @Test
    fun `탈퇴한 사용자는 목록에서 빠진다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val withdrawn = onboardedUser()
        val stays = onboardedUser()
        listOf(withdrawn, stays).forEach { like(publicId, it).andExpect(status().isCreated) }
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        assertEquals(listOf(publicIdOf(stays)), likers(publicId, viewer).data().items().map { it["publicId"] })
    }

    @Test
    fun `행의 pet은 지금 대표 펫의 품종과 생년월이고 펫이 없으면 null이다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val withPet = onboardedUser()
        val oldPet = defaultPet(withPet, breedLabel = "푸들", birthYearMonth = "2021-03")
        val noBirth = onboardedUser()
        defaultPet(noBirth, breedLabel = "진돗개")
        val noPet = onboardedUser()
        listOf(withPet, noBirth, noPet).forEach { like(publicId, it).andExpect(status().isCreated) }
        defaultPet(withPet, breedLabel = "말티즈", birthYearMonth = "2020-08")
        jdbcTemplate.update("update pet_profiles set is_default = false where id = ?", oldPet)

        val pets = likers(publicId, viewer).data().items().associate { it["publicId"] to it["pet"] }

        assertEquals(mapOf("breedLabel" to "말티즈", "birthYearMonth" to "2020-08"), pets[publicIdOf(withPet)], "바뀐 대표 펫이 나와야 한다")
        assertEquals(mapOf("breedLabel" to "진돗개", "birthYearMonth" to null), pets[publicIdOf(noBirth)])
        assertNull(pets[publicIdOf(noPet)])
    }

    @Test
    fun `이용 제한과 만료 전 임시 정지 사용자는 평소처럼 나온다`() {
        val viewer = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        val restricted = onboardedUser()
        val suspended = onboardedUser()
        listOf(restricted, suspended).forEach { like(publicId, it).andExpect(status().isCreated) }
        sanction(restricted, UserStatus.RESTRICTED, null)
        sanction(suspended, UserStatus.SUSPENDED, LocalDateTime.now().plusDays(3))

        assertEquals(
            listOf(suspended, restricted).map(::publicIdOf),
            likers(publicId, viewer).data().items().map { it["publicId"] },
        )
    }

    @Test
    fun `좋아요가 없으면 빈 items와 likeCount 0이다`() {
        likers(createdPublicId(onboardedUser()), onboardedUser())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(0))
            .andExpect(jsonPath("$.data.hasMore").value(false))
            .andExpect(jsonPath("$.data.items.length()").value(0))
    }

    @Test
    fun `지운 게시물과 나를 차단한 작성자는 404 130400이고 내가 차단한 작성자는 403 130001이다`() {
        val viewer = onboardedUser()
        val authorId = onboardedUser()
        val deleted = createdPublicId(authorId)
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        val blockingAuthor = onboardedUser()
        val blockingPost = createdPublicId(blockingAuthor)
        relate(blockingAuthor, viewer, "block")
        val blockedAuthor = onboardedUser()
        val blockedPost = createdPublicId(blockedAuthor)
        relate(viewer, blockedAuthor, "block")

        listOf(deleted, blockingPost).forEach {
            likers(it, viewer)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(130400))
        }
        likers(blockedPost, viewer)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
    }

    @Test
    fun `내 게시물의 좋아요한 사람도 볼 수 있다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val liker = onboardedUser()
        like(publicId, liker).andExpect(status().isCreated)

        val data = likers(publicId, authorId).andExpect(status().isOk).data()

        assertFalse(data.items().isEmpty())
        assertEquals(listOf(publicIdOf(liker)), data.items().map { it["publicId"] })
    }

    private fun likers(
        publicId: String,
        viewerId: Long,
    ): ResultActions =
        mockMvc.perform(
            get("/api/v1/sns/posts/$publicId/likers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"),
        )

    /** 주어진 순서대로 좋아요 행을 넣는다. 뒤에 있을수록 최근에 누른 사람이다 */
    private fun insertLikes(
        publicId: String,
        userIds: List<Long>,
    ) {
        val postId = jdbcTemplate.queryForObject("select id from sns_posts where public_id = ?", Long::class.java, publicId)!!
        jdbcTemplate.batchUpdate(
            "insert into sns_post_likes (post_id, user_id, created_at, updated_at) values (?, ?, now(6), now(6))",
            userIds.map { arrayOf<Any>(postId, it) },
        )
    }

    private fun sanction(
        userId: Long,
        status: UserStatus,
        until: LocalDateTime?,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.status = :st, u.sanctionUntil = :until where u.id = :id")
                .setParameter("st", status)
                .setParameter("until", until)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    private fun ResultActions.data(): Map<String, Any?> = JsonPath.read(andReturn().response.getContentAsString(Charsets.UTF_8), "$.data")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.items(): List<Map<String, Any?>> = this["items"] as List<Map<String, Any?>>
}
