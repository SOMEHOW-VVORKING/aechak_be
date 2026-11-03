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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
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

    /** 대표 펫 하나를 심고 id를 돌려준다. */
    protected fun defaultPet(userId: Long): Long =
        tx.execute {
            val breed = Breed.of(Species.DOG, "말티즈${sequence.incrementAndGet()}")
            em.persist(breed)
            val pet = PetProfile.register(em.find(User::class.java, userId), breed, "코코", isDefault = true)
            em.persist(pet)
            em.flush()
            pet.id
        }!!

    /** 이 사용자가 게시물 사진 용도로 발급받았을 tmp 키. fake 스토리지는 업로드 여부를 보지 않는다. */
    protected fun tmpPhotoKey(
        userId: Long,
        purpose: UploadPurpose = UploadPurpose.SNS_POST_IMAGE,
    ): String = "${FileKey.tmpPrefixOf(userId, purpose)}${Ulid.generate()}.jpg"

    /** 이 tmp 키를 승격한 영구 키. 게시물 사진은 승격마다 새 키라 fake의 기록에서 찾는다. */
    protected fun promotedKeyOf(tmpKey: String): String = fakeFileStorage.promotedKeyOf(tmpKey)

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

    private fun jsonArray(values: List<String>): String = values.joinToString(",", "[", "]") { "\"$it\"" }
}
