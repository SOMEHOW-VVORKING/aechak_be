package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Timestamp
import java.text.Normalizer
import java.time.LocalDateTime

/**
 * 통합: 차단과 뮤트 관리 목록, 팔로워와 팔로잉 목록이 실 보안 필터체인과 MySQL을 지나 계약대로 최근순 커서 목록을 돌려주는지.
 * 깨지면 순서나 totalCount가 어긋나거나 탈퇴 사용자가 남거나 상대의 차단이 내 목록 행을 지우거나 남의 커서가 통과한다.
 * 팔로우 목록에서는 차단 관계 행이 보이거나 검색어가 와일드카드로 동작하거나 정규화 전 검색어가 다른 결과를 낸다.
 */
class SnsListIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `차단 관리 목록은 최근 차단순이고 행에 표시 정보를 싣는다`() {
        val me = onboardedUser("listme")
        val first = onboardedUser("first", imageKey = "users/profile/abc.webp")
        val second = onboardedUser("second")
        relate(me, first.publicId, BLOCKS)
        relate(me, second.publicId, BLOCKS)

        list(me, BLOCKS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(second.publicId, first.publicId)))
            .andExpect(jsonPath("$.data.items[1].nickname").value("first"))
            .andExpect(jsonPath("$.data.items[1].profileImageUrl").value("https://fake-cdn.local/users/profile/abc.webp"))
            .andExpect(jsonPath("$.data.items[0].profileImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `뮤트 관리 목록은 최근 뮤트순이다`() {
        val me = onboardedUser("mutelist")
        val first = onboardedUser("mfirst")
        val second = onboardedUser("msecond")
        relate(me, first.publicId, MUTES)
        relate(me, second.publicId, MUTES)

        list(me, MUTES)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(second.publicId, first.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(2))
    }

    @Test
    fun `첫 페이지에만 totalCount가 있고 커서로 다음 페이지를 이어 받는다`() {
        val me = onboardedUser("pageme")
        val targets = (1..3).map { onboardedUser("page$it") }
        targets.forEach { relate(me, it.publicId, BLOCKS) }

        val firstPage =
            list(me, BLOCKS, size = 2)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[*].publicId", contains(targets[2].publicId, targets[1].publicId)))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
        val cursor = JsonPath.read<String>(firstPage.andReturn().response.contentAsString, "$.data.nextCursor")

        list(me, BLOCKS, size = 2, cursor = cursor)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(targets[0].publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `탈퇴한 사용자는 목록과 totalCount에서 빠진다`() {
        val me = onboardedUser("leaveme")
        val stay = onboardedUser("stay")
        val gone = onboardedUser("gone")
        relate(me, stay.publicId, BLOCKS)
        relate(me, gone.publicId, BLOCKS)
        tx.execute { em.find(User::class.java, gone.id).withdraw() }

        list(me, BLOCKS)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(stay.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `행의 createdAt은 관계 행이 생긴 시각이다`() {
        val me = onboardedUser("timeme")
        val target = onboardedUser("timed")
        val before = dbNow()
        relate(me, target.publicId, MUTES)
        val after = dbNow()
        val stored =
            tx.execute {
                em
                    .createNativeQuery("SELECT created_at FROM sns_mutes WHERE muter_id = ?1")
                    .setParameter(1, me.id)
                    .singleResult as LocalDateTime
            }!!
        assertTrue(
            !stored.isBefore(before) && !stored.isAfter(after),
            "저장된 created_at($stored)이 관계를 만든 요청 전후의 DB 시각($before ~ $after) 사이여야 한다",
        )

        val body =
            list(me, MUTES)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertEquals(
            stored,
            LocalDateTime.parse(JsonPath.read<String>(body, "$.data.items[0].createdAt")),
            "목록 createdAt이 저장된 관계 생성 시각과 같아야 한다",
        )
    }

    @Test
    fun `뮤트한 상대가 나를 차단해도 내 뮤트 목록에 남는다`() {
        val me = onboardedUser("mutekeep")
        val other = onboardedUser("muteblock")
        relate(me, other.publicId, MUTES)
        relate(other, me.publicId, BLOCKS)

        list(me, MUTES)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(other.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `차단한 상대가 나를 차단해도 내 차단 목록에 남고 totalCount에 들어간다`() {
        val me = onboardedUser("blockkeep")
        val other = onboardedUser("blockback")
        relate(me, other.publicId, BLOCKS)
        relate(other, me.publicId, BLOCKS)

        list(me, BLOCKS)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(other.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `잘못된 커서와 다른 종류 목록의 커서는 400 90002다`() {
        val me = onboardedUser("badcursor")
        (1..2).map { onboardedUser("bad$it") }.forEach { relate(me, it.publicId, MUTES) }
        val muteCursor = nextCursor(list(me, MUTES, size = 1))

        expectInvalidCursor(list(me, BLOCKS, cursor = "%%%broken%%%"))
        expectInvalidCursor(list(me, BLOCKS, cursor = muteCursor))
    }

    @Test
    fun `다른 사용자의 차단 목록 커서를 내 차단 목록에 넣으면 400 90002다`() {
        val me = onboardedUser("cursorme")
        val other = onboardedUser("cursorother")
        (1..2).map { onboardedUser("oc$it") }.forEach { relate(other, it.publicId, BLOCKS) }
        val othersCursor = nextCursor(list(other, BLOCKS, size = 1))

        expectInvalidCursor(list(me, BLOCKS, cursor = othersCursor))
    }

    @Test
    fun `size가 범위를 벗어나면 400 90001이다`() {
        val me = onboardedUser("sizeme")

        list(me, BLOCKS, size = 101)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `팔로워 목록은 최근 팔로우순이고 커서로 이어 받으며 totalCount는 null이다`() {
        val owner = onboardedUser("fwowner")
        val fans = (1..3).map { onboardedUser("fan$it") }
        fans.forEach { follow(it, owner.publicId) }

        val firstPage =
            followList(owner, owner, FOLLOWERS, size = 2)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[*].publicId", contains(fans[2].publicId, fans[1].publicId)))
                .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
                .andExpect(jsonPath("$.data.hasNext").value(true))

        followList(owner, owner, FOLLOWERS, size = 2, cursor = nextCursor(firstPage))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(fans[0].publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `팔로잉 목록은 주인이 팔로우한 사용자를 최근 팔로우순으로 준다`() {
        val owner = onboardedUser("fgowner")
        val stars = (1..2).map { onboardedUser("star$it") }
        stars.forEach { follow(owner, it.publicId) }
        val viewer = onboardedUser("fgviewer")

        followList(viewer, owner, FOLLOWINGS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(stars[1].publicId, stars[0].publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
    }

    @Test
    fun `닉네임 부분 일치로 거르고 맞는 사용자가 없으면 200과 빈 목록이다`() {
        val owner = onboardedUser("nkowner")
        val coco = onboardedUser("코코집사")
        val bori = onboardedUser("보리언니")
        follow(coco, owner.publicId)
        follow(bori, owner.publicId)

        followList(owner, owner, FOLLOWERS, nickname = "코코")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(coco.publicId)))
        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "몽이"))
    }

    @Test
    fun `차단 관계인 사용자 행은 숨기고 뮤트한 사용자는 보이며 프로필의 수는 그대로다`() {
        val viewer = onboardedUser("hideviewer")
        val owner = onboardedUser("hideowner")
        val shown = onboardedUser("shown")
        val blockedByViewer = onboardedUser("byviewer")
        val blockingViewer = onboardedUser("blocksme")
        val muted = onboardedUser("mutedfan")
        listOf(shown, blockedByViewer, blockingViewer, muted).forEach { follow(it, owner.publicId) }
        relate(viewer, blockedByViewer.publicId, BLOCKS)
        relate(blockingViewer, viewer.publicId, BLOCKS)
        relate(viewer, muted.publicId, MUTES)

        followList(viewer, owner, FOLLOWERS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(muted.publicId, shown.publicId)))
        mockMvc
            .perform(
                get("/api/v1/sns/users/${owner.publicId}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.followerCount").value(4))
    }

    @Test
    fun `목록 주인이 나를 차단하면 404 130000이고 내가 주인을 차단하면 403 130001이다`() {
        val viewer = onboardedUser("ownerblk")
        val blocksMe = onboardedUser("ownerblkme")
        val blockedByMe = onboardedUser("ownerblked")
        relate(blocksMe, viewer.publicId, BLOCKS)
        relate(viewer, blockedByMe.publicId, BLOCKS)

        followList(viewer, blocksMe, FOLLOWERS)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
        followList(viewer, blockedByMe, FOLLOWINGS)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
    }

    @Test
    fun `없거나 탈퇴한 목록 주인은 404 130000이다`() {
        val viewer = onboardedUser("noowner")
        val gone = onboardedUser("goneowner")
        tx.execute { em.find(User::class.java, gone.id).withdraw() }

        followList(viewer, Seeded(0L, "01JZZZZZZZZZZZZZZZZZZZZZZZ"), FOLLOWERS)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
        followList(viewer, gone, FOLLOWERS)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
    }

    @Test
    fun `탈퇴한 팔로워는 목록에서 빠진다`() {
        val owner = onboardedUser("wdowner")
        val stay = onboardedUser("wdstay")
        val gone = onboardedUser("wdgone")
        follow(stay, owner.publicId)
        follow(gone, owner.publicId)
        tx.execute { em.find(User::class.java, gone.id).withdraw() }

        followList(owner, owner, FOLLOWERS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(stay.publicId)))
    }

    @Test
    fun `행에 펫 요약, 본인 행 여부, 보는 사람의 팔로우 여부를 싣는다`() {
        val owner = onboardedUser("rowowner")
        val viewer = onboardedUser("rowviewer")
        val petOwner = onboardedUser("rowpet", imageKey = "users/profile/pet.webp")
        val plain = onboardedUser("rowplain")
        tx.execute {
            val user = em.find(User::class.java, petOwner.id)
            val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
            val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
            em.persist(PetProfile.register(user, maltese, "sub"))
            em.persist(PetProfile.register(user, poodle, "main", birthYearMonth = "2021-03", isDefault = true))
        }
        follow(plain, owner.publicId)
        follow(petOwner, owner.publicId)
        follow(viewer, owner.publicId)
        follow(viewer, petOwner.publicId)

        followList(viewer, owner, FOLLOWERS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(viewer.publicId, petOwner.publicId, plain.publicId)))
            .andExpect(jsonPath("$.data.items[0].isMe").value(true))
            .andExpect(jsonPath("$.data.items[0].following").value(false))
            .andExpect(jsonPath("$.data.items[1].isMe").value(false))
            .andExpect(jsonPath("$.data.items[1].following").value(true))
            .andExpect(jsonPath("$.data.items[1].profileImageUrl").value("https://fake-cdn.local/users/profile/pet.webp"))
            .andExpect(jsonPath("$.data.items[1].petSummary.breedLabel").value("Poodle"))
            .andExpect(jsonPath("$.data.items[1].petSummary.activePetCount").value(2))
            .andExpect(jsonPath("$.data.items[2].following").value(false))
            .andExpect(jsonPath("$.data.items[2].petSummary").value(nullValue()))
            .andExpect(jsonPath("$.data.items[2].profileImageUrl").value(nullValue()))
    }

    @Test
    fun `응답 행에 내부 id와 펫 생년월이 없다`() {
        val owner = onboardedUser("leakowner")
        val fan = onboardedUser("leakfan")
        tx.execute {
            val breed = Breed.of(Species.CAT, "Korean").also { em.persist(it) }
            em.persist(PetProfile.register(em.find(User::class.java, fan.id), breed, "cat", birthYearMonth = "2020-11", isDefault = true))
        }
        follow(fan, owner.publicId)

        val body =
            followList(owner, owner, FOLLOWERS)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val item = JsonPath.read<Map<String, Any?>>(body, "$.data.items[0]")
        val pet = JsonPath.read<Map<String, Any?>>(body, "$.data.items[0].petSummary")

        assertEquals(
            setOf("publicId", "nickname", "profileImageUrl", "petSummary", "isMe", "following"),
            item.keys,
            "목록 행은 계약의 여섯 필드만 실어야 한다",
        )
        assertEquals(setOf("breedLabel", "activePetCount"), pet.keys, "펫 요약은 품종과 ACTIVE 수만 실어야 한다")
    }

    @Test
    fun `잘못된 커서와 다른 주인, 다른 검색어, 팔로잉 목록의 커서는 400 90002다`() {
        val owner = onboardedUser("curowner")
        val other = onboardedUser("curother")
        (1..2).map { onboardedUser("cur$it") }.forEach {
            follow(it, owner.publicId)
            follow(it, other.publicId)
            follow(owner, it.publicId)
        }
        val othersCursor = nextCursor(followList(owner, other, FOLLOWERS, size = 1))
        val searchCursor = nextCursor(followList(owner, owner, FOLLOWERS, size = 1, nickname = "cur"))
        val followingCursor = nextCursor(followList(owner, owner, FOLLOWINGS, size = 1))

        expectInvalidCursor(followList(owner, owner, FOLLOWERS, cursor = "%%%broken%%%"))
        expectInvalidCursor(followList(owner, owner, FOLLOWERS, cursor = othersCursor))
        expectInvalidCursor(followList(owner, owner, FOLLOWERS, cursor = searchCursor))
        expectInvalidCursor(followList(owner, owner, FOLLOWERS, cursor = searchCursor, nickname = "cur1"))
        expectInvalidCursor(followList(owner, owner, FOLLOWERS, cursor = followingCursor))
    }

    @Test
    fun `특수문자나 자모처럼 아무와도 맞지 않는 검색어는 200과 빈 목록이다`() {
        val owner = onboardedUser("jamoowner")
        follow(onboardedUser("코코"), owner.publicId)

        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "ㅋ"))
        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "!@#"))
    }

    @Test
    fun `퍼센트와 밑줄 검색어는 와일드카드로 동작하지 않아 200과 빈 목록이다`() {
        val owner = onboardedUser("wildowner")
        follow(onboardedUser("wildfan"), owner.publicId)

        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "%"))
        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "_"))
        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "w%n"))
    }

    @Test
    fun `13자 검색어는 200과 빈 목록이다`() {
        val owner = onboardedUser("longowner")
        follow(onboardedUser("longfan"), owner.publicId)

        expectEmptyPage(followList(owner, owner, FOLLOWERS, nickname = "longfanlongf1"))
    }

    @Test
    fun `공백만 있는 검색어는 거르지 않은 목록과 같다`() {
        val owner = onboardedUser("blankowner")
        val fans = (1..2).map { onboardedUser("blank$it") }
        fans.forEach { follow(it, owner.publicId) }

        followList(owner, owner, FOLLOWERS, nickname = "   ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(fans[1].publicId, fans[0].publicId)))
    }

    @Test
    fun `앞뒤 공백과 NFD 검색어는 정규화한 검색어와 같은 결과다`() {
        val owner = onboardedUser("nfdowner")
        val coco = onboardedUser("코코집사")
        follow(coco, owner.publicId)
        follow(onboardedUser("보리언니"), owner.publicId)

        followList(owner, owner, FOLLOWERS, nickname = "  코코 ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(coco.publicId)))
        followList(owner, owner, FOLLOWERS, nickname = Normalizer.normalize("코코", Normalizer.Form.NFD))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(coco.publicId)))
    }

    @Test
    fun `차단 관계가 하나도 없는 보는 사람도 목록을 정상으로 받는다`() {
        val viewer = onboardedUser("cleanview")
        val owner = onboardedUser("cleanowner")
        val fan = onboardedUser("cleanfan")
        follow(fan, owner.publicId)

        followList(viewer, owner, FOLLOWERS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(fan.publicId)))
    }

    private fun dbNow(): LocalDateTime =
        tx.execute {
            when (val now = em.createNativeQuery("SELECT NOW(6)").singleResult) {
                is LocalDateTime -> now
                is Timestamp -> now.toLocalDateTime()
                else -> error("NOW(6) 결과 타입을 예상하지 못했다: ${now?.javaClass}")
            }
        }!!

    private fun relate(
        actor: Seeded,
        targetPublicId: String,
        kind: String,
    ) {
        val action = if (kind == BLOCKS) "block" else "mute"
        mockMvc
            .perform(
                post("/api/v1/sns/users/$targetPublicId/$action")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
            ).andExpect(status().isCreated)
    }

    private fun follow(
        actor: Seeded,
        targetPublicId: String,
    ) {
        mockMvc
            .perform(
                post("/api/v1/sns/users/$targetPublicId/follow")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
            ).andExpect(status().isCreated)
    }

    private fun followList(
        viewer: Seeded,
        owner: Seeded,
        direction: String,
        size: Int? = null,
        cursor: String? = null,
        nickname: String? = null,
    ): ResultActions {
        val request =
            get("/api/v1/sns/users/${owner.publicId}/$direction")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}")
        size?.let { request.param("size", it.toString()) }
        cursor?.let { request.param("cursor", it) }
        nickname?.let { request.param("nickname", it) }
        return mockMvc.perform(request)
    }

    private fun expectEmptyPage(actions: ResultActions) {
        actions
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isEmpty)
            .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    private fun list(
        viewer: Seeded,
        kind: String,
        size: Int? = null,
        cursor: String? = null,
    ): ResultActions {
        val request =
            get("/api/v1/sns/me/$kind")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}")
        size?.let { request.param("size", it.toString()) }
        cursor?.let { request.param("cursor", it) }
        return mockMvc.perform(request)
    }

    private fun nextCursor(actions: ResultActions): String =
        JsonPath.read(
            actions
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString,
            "$.data.nextCursor",
        )

    private fun expectInvalidCursor(actions: ResultActions) {
        actions
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    private data class Seeded(
        val id: Long,
        val publicId: String,
    )

    /** 온보딩을 마쳐 프로필 행이 있는 ACTIVE 사용자를 심는다. */
    private fun onboardedUser(
        nickname: String,
        imageKey: String? = null,
    ): Seeded =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            if (imageKey != null) user.updateProfile(nickname, null, imageKey)
            em.flush()
            Seeded(user.id, user.publicId)
        }!!

    companion object {
        private const val BLOCKS = "blocks"
        private const val MUTES = "mutes"
        private const val FOLLOWERS = "followers"
        private const val FOLLOWINGS = "followings"
    }
}
