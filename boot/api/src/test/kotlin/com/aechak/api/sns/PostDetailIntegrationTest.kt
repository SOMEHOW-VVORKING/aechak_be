package com.aechak.api.sns

import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 통합 — 게시물 상세가 볼 수 없는 경우를 계약 순서대로 거르고 작성자, 펫 태그, 미디어, 관계 상태를 싣는지.
 */
class PostDetailIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `없는 publicId와 형식이 틀린 publicId는 404 130400이다`() {
        val viewerId = onboardedUser()

        expectUnavailable(getPost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", viewerId))
        expectUnavailable(getPost("not-a-ulid", viewerId))
    }

    @Test
    fun `탈퇴한 작성자의 게시물은 130400이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        tx.execute { em.find(User::class.java, authorId).withdraw() }

        expectUnavailable(getPost(publicId, onboardedUser()))
    }

    @Test
    fun `나를 차단한 작성자의 게시물은 130400이다`() {
        val authorId = onboardedUser()
        val viewerId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(authorId, viewerId, "block")

        expectUnavailable(getPost(publicId, viewerId))
    }

    @Test
    fun `서로 차단했으면 130400이다`() {
        val authorId = onboardedUser()
        val viewerId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(authorId, viewerId, "block")
        relate(viewerId, authorId, "block")

        expectUnavailable(getPost(publicId, viewerId))
    }

    @Test
    fun `내가 차단한 작성자의 게시물은 403 130001이고 작성자와 내용을 싣지 않는다`() {
        val authorId = onboardedUser()
        val viewerId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(viewerId, authorId, "block")

        getPost(publicId, viewerId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `뮤트한 작성자의 게시물도 보이고 mutedByMe가 true다`() {
        val authorId = onboardedUser()
        val viewerId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(viewerId, authorId, "mute")

        assertEquals(true, postDetail(publicId, viewerId)["mutedByMe"])
    }

    @Test
    fun `팔로우 중인 작성자면 following이 true다`() {
        val authorId = onboardedUser()
        val viewerId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(viewerId, authorId, "follow")

        assertEquals(true, postDetail(publicId, viewerId)["following"])
    }

    @Test
    fun `정지 상태인 작성자의 게시물도 평소처럼 보인다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        tx.execute {
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.SUSPENDED)
                .setParameter("id", authorId)
                .executeUpdate()
        }

        assertEquals("산책 다녀왔어요", postDetail(publicId, onboardedUser())["caption"])
    }

    @Test
    fun `펫 태그에 게시할 때 대표 펫의 품종과 생년월을 싣는다`() {
        val authorId = onboardedUser()
        defaultPet(authorId, breedLabel = "골든리트리버", birthYearMonth = "2021-03")
        val publicId = createdPublicId(authorId)

        assertEquals(mapOf("breedLabel" to "골든리트리버", "birthYearMonth" to "2021-03"), postDetail(publicId)["pet"])
    }

    @Test
    fun `생년월이 없는 펫은 생년월이 null이다`() {
        val authorId = onboardedUser()
        defaultPet(authorId, breedLabel = "푸들")
        val publicId = createdPublicId(authorId)

        assertEquals(mapOf("breedLabel" to "푸들", "birthYearMonth" to null), postDetail(publicId)["pet"])
    }

    @Test
    fun `펫을 등록하지 않은 작성자의 게시물은 pet이 null이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        assertNull(postDetail(publicId)["pet"])
    }

    @Test
    fun `대표 펫을 바꿔도 지난 게시물의 펫은 그대로다`() {
        val authorId = onboardedUser()
        val firstPetId = defaultPet(authorId, breedLabel = "골든리트리버")
        val publicId = createdPublicId(authorId)
        val secondPetId = defaultPet(authorId, breedLabel = "푸들", isDefault = false)
        jdbcTemplate.update("update pet_profiles set is_default = (id = ?) where user_id = ?", secondPetId, authorId)

        assertEquals("골든리트리버", (postDetail(publicId)["pet"] as Map<*, *>)["breedLabel"])
        assertEquals(firstPetId, (postRow(publicId)["pet_id"] as Number).toLong())
    }

    @Test
    fun `태그된 펫을 지우면 펫 태그가 사라진다`() {
        val authorId = onboardedUser()
        val petId = defaultPet(authorId)
        val publicId = createdPublicId(authorId)
        jdbcTemplate.update("update pet_profiles set status = 'DELETED' where id = ?", petId)

        assertNull(postDetail(publicId)["pet"])
    }

    @Test
    fun `태그된 펫의 품종을 고치면 새 품종이 보인다`() {
        val authorId = onboardedUser()
        val petId = defaultPet(authorId, breedLabel = "골든리트리버")
        val publicId = createdPublicId(authorId)
        val newBreedId =
            tx.execute {
                Breed.of(Species.DOG, "래브라도리트리버").also { em.persist(it) }.id
            }!!
        jdbcTemplate.update("update pet_profiles set breed_id = ? where id = ?", newBreedId, petId)

        assertEquals("래브라도리트리버", (postDetail(publicId)["pet"] as Map<*, *>)["breedLabel"])
    }

    @Test
    fun `사진은 작성자가 정한 순서로 싣는다`() {
        val authorId = onboardedUser()
        val tmpKeys = List(3) { tmpPhotoKey(authorId) }.shuffled()
        val publicId = createdPublicId(authorId, postBody(photoKeys = tmpKeys))

        assertEquals(tmpKeys.map { "https://fake-cdn.local/${promotedKeyOf(it)}" }, postDetail(publicId)["photoUrls"])
    }

    @Test
    fun `캡션 없는 사진 게시물은 caption이 null이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId, postBody(caption = null, photoKeys = listOf(tmpPhotoKey(authorId))))

        val detail = postDetail(publicId)

        assertNull(detail["caption"])
        assertEquals(1, (detail["photoUrls"] as List<*>).size)
    }

    @Test
    fun `텍스트 게시물은 사진이 없고 영상이 null이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId, postBody(caption = "산책 코스 추천받아요"))

        val detail = postDetail(publicId)

        assertEquals("산책 코스 추천받아요", detail["caption"])
        assertEquals(emptyList<Any>(), detail["photoUrls"])
        assertNull(detail["video"])
    }

    @Test
    fun `좋아요와 댓글과 리포스트 수는 0이고 저장 수 필드는 없다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        getPost(publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(0))
            .andExpect(jsonPath("$.data.commentCount").value(0))
            .andExpect(jsonPath("$.data.repostCount").value(0))
            .andExpect(jsonPath("$.data.saveCount").doesNotExist())
    }

    @Test
    fun `작성자 본인이 보면 isMine이 true다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        val detail = postDetail(publicId, authorId)

        assertEquals(true, detail["isMine"])
        assertEquals(false, detail["following"])
        assertEquals(false, detail["edited"])
    }

    @Test
    fun `응답 필드는 계약과 같고 내부 id와 저장 키를 싣지 않는다`() {
        val authorId = onboardedUser()
        defaultPet(authorId)
        val publicId = createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId))))

        val detail = postDetail(publicId, onboardedUser())

        val contractFields =
            (
                "publicId author pet caption hashtags photoUrls video likeCount commentCount repostCount " +
                    "edited createdAt isMine following mutedByMe"
            ).split(" ").toSet()
        assertEquals(contractFields, detail.keys)
        assertEquals(setOf("publicId", "nickname", "profileImageUrl"), (detail["author"] as Map<*, *>).keys)
        assertEquals(publicIdOf(authorId), (detail["author"] as Map<*, *>)["publicId"])
        assertEquals(publicId, detail["publicId"])
        assertEquals(emptyList<Any>(), detail["hashtags"])
        assertNotNull(detail["createdAt"])
    }

    private fun expectUnavailable(result: ResultActions) {
        result
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
    }
}
