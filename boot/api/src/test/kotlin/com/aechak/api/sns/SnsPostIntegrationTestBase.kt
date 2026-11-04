package com.aechak.api.sns

import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.support.Ulid
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import com.aechak.webcommon.http.CustomHttpHeaders
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 게시물 통합 테스트 공용. 실제 보안 필터체인을 지나는 MockMvc, 사용자와 펫 준비, 게시 요청, DB 확인을 둔다.
 * 공유 FakeFileStorage를 쓰므로 테스트마다 reset()으로 기록을 비운다.
 */
abstract class SnsPostIntegrationTestBase : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var fileStorage: FileStorage

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected lateinit var mockMvc: MockMvc

    private val sequence = AtomicInteger()

    protected val fakeFileStorage: FakeFileStorage get() = fileStorage as FakeFileStorage

    @BeforeEach
    fun setUpSnsPost() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        fakeFileStorage.reset()
    }

    /** 온보딩을 마쳐 닉네임이 있는 ACTIVE 사용자를 심고 id를 돌려준다. */
    protected fun onboardedUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding("집사${sequence.incrementAndGet()}")
            em.flush()
            user.id
        }!!

    /** 펫 하나를 심고 id를 돌려준다. 기본은 대표 펫이다. */
    protected fun defaultPet(
        userId: Long,
        breedLabel: String = "말티즈${sequence.incrementAndGet()}",
        birthYearMonth: String? = null,
        isDefault: Boolean = true,
    ): Long =
        tx.execute {
            val breed = Breed.of(Species.DOG, breedLabel)
            em.persist(breed)
            val user = em.find(User::class.java, userId)
            val pet = PetProfile.register(user, breed, "코코", birthYearMonth = birthYearMonth, isDefault = isDefault)
            em.persist(pet)
            em.flush()
            pet.id
        }!!

    /** 이 사용자가 게시물 사진 용도로 발급받았을 tmp 키. fake 스토리지는 업로드 여부를 보지 않는다. */
    protected fun tmpPhotoKey(
        userId: Long,
        purpose: UploadPurpose = UploadPurpose.SNS_POST_IMAGE,
    ): String = "${FileKey.tmpPrefixOf(userId, purpose)}${Ulid.generate()}.jpg"

    /** 이 사용자가 게시물 영상 용도로 발급받았을 tmp 키. */
    protected fun tmpVideoKey(userId: Long): String = "${FileKey.tmpPrefixOf(userId, UploadPurpose.SNS_POST_VIDEO)}${Ulid.generate()}.mp4"

    /** 이 tmp 키를 승격한 영구 키. 게시물 사진은 승격마다 새 키라 fake의 기록에서 찾는다. */
    protected fun promotedKeyOf(tmpKey: String): String = fakeFileStorage.promotedKeyOf(tmpKey)

    protected fun videoJson(
        key: String,
        thumbnailKey: String,
        durationMs: Int = 42_500,
    ): String = """{"key":"$key","thumbnailKey":"$thumbnailKey","durationMs":$durationMs}"""

    protected fun postBody(
        caption: String? = "산책 다녀왔어요",
        photoKeys: List<String> = emptyList(),
        hashtags: List<String> = emptyList(),
        video: String = "null",
    ): String {
        val captionJson = caption?.let { "\"$it\"" } ?: "null"
        return """{"caption":$captionJson,"photoKeys":${jsonArray(photoKeys)},"video":$video,"hashtags":${jsonArray(hashtags)}}"""
    }

    protected fun createPost(
        userId: Long,
        body: String,
        idempotencyKey: String? = Ulid.generate(),
    ): ResultActions {
        val request =
            post("/api/v1/sns/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        if (idempotencyKey != null) request.header(CustomHttpHeaders.IDEMPOTENCY_KEY, idempotencyKey)
        return mockMvc.perform(request)
    }

    /** 게시하고 만든 게시물의 publicId를 돌려준다. */
    protected fun createdPublicId(
        userId: Long,
        body: String = postBody(),
        idempotencyKey: String = Ulid.generate(),
    ): String {
        val json =
            createPost(userId, body, idempotencyKey)
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data.publicId")
    }

    protected fun publicIdOf(userId: Long): String =
        jdbcTemplate.queryForObject("select public_id from users where id = ?", String::class.java, userId)!!

    protected fun getPost(
        publicId: String,
        viewerId: Long? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/posts/$publicId")
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return mockMvc.perform(request)
    }

    protected fun deletePost(
        publicId: String,
        userId: Long,
    ): ResultActions =
        mockMvc.perform(
            delete("/api/v1/sns/posts/$publicId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"),
        )

    protected fun repost(
        publicId: String,
        userId: Long,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/sns/posts/$publicId/repost")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"),
        )

    protected fun unrepost(
        publicId: String,
        userId: Long,
    ): ResultActions =
        mockMvc.perform(
            delete("/api/v1/sns/posts/$publicId/repost")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"),
        )

    protected fun updateBody(
        caption: String? = "공원 다녀왔어요",
        hashtags: List<String> = emptyList(),
    ): String {
        val captionJson = caption?.let { "\"$it\"" } ?: "null"
        return """{"caption":$captionJson,"hashtags":${jsonArray(hashtags)}}"""
    }

    protected fun updatePost(
        publicId: String,
        userId: Long,
        body: String,
    ): ResultActions =
        mockMvc.perform(
            patch("/api/v1/sns/posts/$publicId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    /** 상세를 200으로 받고 data 객체를 돌려준다. */
    protected fun postDetail(
        publicId: String,
        viewerId: Long? = null,
    ): Map<String, Any?> {
        val json =
            getPost(publicId, viewerId)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    /** actor가 target에게 차단, 뮤트, 팔로우(block, mute, follow) 중 하나를 건다. */
    protected fun relate(
        actorId: Long,
        targetId: Long,
        action: String,
    ) {
        mockMvc
            .perform(
                post("/api/v1/sns/users/${publicIdOf(targetId)}/$action")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actorId)}"),
            ).andExpect(status().is2xxSuccessful)
    }

    /** actor가 target에게 건 차단, 뮤트, 팔로우(block, mute, follow) 중 하나를 푼다. */
    protected fun unrelate(
        actorId: Long,
        targetId: Long,
        action: String,
    ) {
        mockMvc
            .perform(
                delete("/api/v1/sns/users/${publicIdOf(targetId)}/$action")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actorId)}"),
            ).andExpect(status().is2xxSuccessful)
    }

    /** 게시 시각을 옮긴다. 피드의 기간 경계와 순서는 이 시각으로 만든다. */
    protected fun backdatePost(
        publicId: String,
        createdAt: LocalDateTime,
    ) {
        jdbcTemplate.update("update sns_posts set created_at = ? where public_id = ?", createdAt, publicId)
    }

    protected fun postCreatedAt(publicId: String): LocalDateTime =
        jdbcTemplate.queryForObject("select created_at from sns_posts where public_id = ?", LocalDateTime::class.java, publicId)!!

    /** 피드(following, explore, popular)를 부른다. */
    protected fun getFeed(
        kind: String,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/feeds/$kind")
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return mockMvc.perform(request)
    }

    /** 피드 한 페이지를 200으로 받고 data 객체를 돌려준다. */
    protected fun feedPage(
        kind: String,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> {
        val json =
            getFeed(kind, viewerId, cursor, size)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    /** 피드 페이지의 항목. */
    @Suppress("UNCHECKED_CAST")
    protected fun Map<String, Any?>.feedItems(): List<Map<String, Any?>> = this["items"] as List<Map<String, Any?>>

    /** 피드 페이지 카드의 게시물 publicId를 순서대로 돌려준다. */
    protected fun Map<String, Any?>.postIds(): List<String> = feedItems().map { (it["post"] as Map<*, *>)["publicId"] as String }

    protected fun postRow(publicId: String): Map<String, Any?> =
        jdbcTemplate.queryForMap("select * from sns_posts where public_id = ?", publicId)

    protected fun postCountOf(authorId: Long): Int =
        jdbcTemplate.queryForObject("select count(*) from sns_posts where author_id = ?", Int::class.java, authorId)!!

    protected fun mediaRowCount(): Int = jdbcTemplate.queryForObject("select count(*) from sns_post_media", Int::class.java)!!

    /** 게시물 미디어의 저장 키를 sort_order 순으로 돌려준다. */
    protected fun mediaKeysOf(publicId: String): List<String> =
        jdbcTemplate.queryForList(
            "select m.storage_key from sns_post_media m join sns_posts p on p.id = m.post_id where p.public_id = ? order by m.sort_order",
            String::class.java,
            publicId,
        )

    /** 작성자 통계 행의 post_count. 행이 없으면 null이다. */
    protected fun statsPostCount(userId: Long): Int? =
        jdbcTemplate
            .queryForList("select post_count from sns_user_stats where user_id = ?", Int::class.java, userId)
            .firstOrNull()

    /** 원글 통계 행의 repost_count. 행이 없으면 null이다. */
    protected fun repostCountOf(publicId: String): Int? =
        jdbcTemplate
            .queryForList(
                "select s.repost_count from sns_post_stats s join sns_posts p on p.id = s.post_id where p.public_id = ?",
                Int::class.java,
                publicId,
            ).firstOrNull()

    /** reposter가 이 원글에 한 리포스트 행 수. */
    protected fun repostRowCount(
        reposterId: Long,
        publicId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_reposts r join sns_posts p on p.id = r.post_id where r.reposter_id = ? and p.public_id = ?",
            Int::class.java,
            reposterId,
            publicId,
        )!!

    /** 해시태그 행마다 이름과 post_count. */
    protected fun hashtagCounts(): Map<String, Int> =
        jdbcTemplate
            .queryForList("select name, post_count from sns_hashtags")
            .associate { it["name"] as String to (it["post_count"] as Number).toInt() }

    /** 작업마다 스레드 하나를 두고 한꺼번에 출발시켜 모두 끝날 때까지 기다린 뒤 던진 예외를 모아 돌려준다. */
    protected fun runConcurrently(tasks: List<() -> Unit>): List<Throwable> {
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(tasks.size)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        try {
            val futures =
                tasks.map { task ->
                    pool.submit {
                        ready.countDown()
                        start.await()
                        try {
                            task()
                        } catch (e: Throwable) {
                            errors.add(e)
                        }
                    }
                }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown() // 모든 스레드가 동시에 출발
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        return errors
    }

    private fun jsonArray(values: List<String>): String = values.joinToString(",", "[", "]") { "\"$it\"" }
}
