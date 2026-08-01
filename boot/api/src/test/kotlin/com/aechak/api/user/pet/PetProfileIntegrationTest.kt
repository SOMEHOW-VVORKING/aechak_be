package com.aechak.api.user.pet

import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.websecurity.config.JwtConfig
import com.jayway.jsonpath.JsonPath
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

/**
 * 통합 — 펫 API가 실 보안 필터체인·실 MySQL을 통과해 계약대로 동작하는지.
 * 깨지면 인가가 뚫렸거나 응답 계약이 프론트와 어긋난 것이다.
 *
 * 한글 응답은 MockMvc 기본 charset을 피해 UTF-8로 직접 파싱함.
 */
class PetProfileIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Autowired
    private lateinit var fileStorage: FileStorage

    private lateinit var mockMvc: MockMvc
    private var ownerId = 0L
    private var otherId = 0L
    private lateinit var ownerToken: String
    private lateinit var otherToken: String
    private var dogBreedId = 0L
    private var catBreedId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        ownerId = createActiveUser()
        otherId = createActiveUser()
        ownerToken = mintAccessToken(ownerId)
        otherToken = mintAccessToken(otherId)
        dogBreedId = createBreed(Species.DOG, "말티즈")
        catBreedId = createBreed(Species.CAT, "코리안숏헤어")
        (fileStorage as FakeFileStorage).clearPromoted()
    }

    @Test
    fun `첫 펫을 등록하면 201이고 요청과 무관하게 대표가 된다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", isDefault = false)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.petId").exists())
            .andExpect(jsonPath("$.data.userId").value(ownerId))
            .andExpect(jsonPath("$.data.breedLabel").value("말티즈"))
            .andExpect(jsonPath("$.data.isDefault").value(true))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
    }

    @Test
    fun `isDefault 키를 아예 안 보내도 정상 등록된다`() {
        val body =
            """
            {
              "name": "초코",
              "breedId": $dogBreedId
            }
            """.trimIndent()

        mockMvc
            .perform(postPet(ownerToken, body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.isDefault").value(true))
    }

    @Test
    fun `isDefault를 null로 명시해 보내도 파싱이 깨지지 않는다`() {
        // Boolean+기본값이면 여기서 파싱이 깨짐. 키를 생략하는 위 케이스는
        // kotlin 모듈이 기본값을 채워 통과하므로 그 회귀를 못 잡음.
        val body =
            """
            {
              "name": "초코",
              "breedId": $dogBreedId,
              "isDefault": null
            }
            """.trimIndent()

        mockMvc
            .perform(postPet(ownerToken, body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.isDefault").value(true))
    }

    @Test
    fun `둘째 펫은 비대표이고 대표로 등록하면 기존 대표가 풀린다`() {
        val firstId = registerPet(ownerToken, petJson(name = "초코"))
        registerPet(ownerToken, petJson(name = "보리"))

        val afterSecond = listBody(ownerToken)
        assertEquals(2, (JsonPath.read(afterSecond, "$.data.pets") as List<*>).size)
        assertEquals(firstId, readId(afterSecond, "$.data.pets[0].petId"), "대표가 최상단이어야 한다")

        registerPet(ownerToken, petJson(name = "모카", isDefault = true))

        val body = listBody(ownerToken)
        val defaults = JsonPath.read<List<*>>(body, "$.data.pets[?(@.isDefault == true)]")
        assertEquals(1, defaults.size, "대표는 항상 한 마리여야 한다")
        assertEquals("모카", JsonPath.read<String>(body, "$.data.pets[0].name"))
    }

    @Test
    fun `대표 해제는 남의 펫까지 건드리지 않는다`() {
        // 해제 쿼리에서 user_id 조건이 빠지면 전 사용자의 대표가 풀림.
        // 내 목록만 보면 안 잡혀서 타인 상태를 직접 확인함.
        registerPet(otherToken, petJson(name = "남의대표"))
        registerPet(ownerToken, petJson(name = "내첫째"))

        registerPet(ownerToken, petJson(name = "내둘째", isDefault = true))

        val otherBody = listBody(otherToken)
        assertEquals(1, (JsonPath.read(otherBody, "$.data.pets") as List<*>).size)
        assertTrue(
            JsonPath.read<Boolean>(otherBody, "$.data.pets[0].isDefault"),
            "타인의 대표 펫은 그대로여야 한다",
        )
    }

    @Test
    fun `비대표끼리는 등록순으로 줄선다`() {
        val first = registerPet(ownerToken, petJson(name = "첫째"))
        val second = registerPet(ownerToken, petJson(name = "둘째"))
        val third = registerPet(ownerToken, petJson(name = "셋째"))

        // 정렬 키를 id로 바꿔도 통과하지 않게 id 순서와 created_at 순서를 어긋나게 둠
        setCreatedAt(third, "2020-01-01T00:00:00")
        setCreatedAt(second, "2021-01-01T00:00:00")
        setCreatedAt(first, "2022-01-01T00:00:00")

        val body = listBody(ownerToken)
        assertEquals("첫째", JsonPath.read<String>(body, "$.data.pets[0].name"), "대표는 created_at과 무관하게 최상단")
        assertEquals("셋째", JsonPath.read<String>(body, "$.data.pets[1].name"), "비대표는 created_at 오름차순")
        assertEquals("둘째", JsonPath.read<String>(body, "$.data.pets[2].name"))
    }

    @Test
    fun `열한 마리째는 한도 초과로 거절한다`() {
        repeat(10) { registerPet(ownerToken, petJson(name = "펫$it")) }

        mockMvc
            .perform(postPet(ownerToken, petJson(name = "열한번째")))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_LIMIT_EXCEEDED.code))
    }

    @Test
    fun `없는 품종이면 거절한다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", breedId = 999_999L)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.INVALID_BREED.code))
    }

    @Test
    fun `연도만 보내면 1월로 저장된다`() {
        registerPet(ownerToken, petJson(name = "초코", birthYearMonth = "2022"))

        assertEquals("2022-01", JsonPath.read<String>(listBody(ownerToken), "$.data.pets[0].birthYearMonth"))
    }

    @Test
    fun `생년월을 안 보내면 null로 저장된다 - 모름이라는 정상 상태다`() {
        // @Pattern은 null을 검사하지 않음. isDefault와 달리 null이 도메인 값(생년월 모름)이라 접지 않음.
        val body =
            """
            {
              "name": "초코",
              "breedId": $dogBreedId
            }
            """.trimIndent()
        mockMvc.perform(postPet(ownerToken, body)).andExpect(status().isCreated)

        assertNull(
            JsonPath.read<String?>(listBody(ownerToken), "$.data.pets[0].birthYearMonth"),
            "생년월 미입력은 null로 남아야 한다 — 기본값을 지어내지 않는다",
        )
    }

    @Test
    fun `미래 생년월은 거절한다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", birthYearMonth = "2099-12")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `체중이 범위를 벗어나면 거절한다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", weight = "200.1")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `사진 키는 정식 위치로 승격되어 저장된다`() {
        registerPet(ownerToken, petJson(name = "초코", profileImageKey = tmpKey(ownerId)))

        val body = listBody(ownerToken)
        val key = JsonPath.read<String>(body, "$.data.pets[0].profileImageKey")
        assertTrue(key.startsWith("pets/profile/"), "tmp가 아니라 정식 접두여야 한다: $key")
        assertEquals(
            "https://fake-cdn.local/$key",
            JsonPath.read<String>(body, "$.data.pets[0].profileImageUrl"),
        )
    }

    @Test
    fun `사진이 없으면 표시용 URL도 null이다`() {
        registerPet(ownerToken, petJson(name = "초코"))

        val body = listBody(ownerToken)
        assertNull(JsonPath.read<String?>(body, "$.data.pets[0].profileImageKey"))
        assertNull(JsonPath.read<String?>(body, "$.data.pets[0].profileImageUrl"))
    }

    @Test
    fun `타인이 발급받은 사진 키는 등록에 쓸 수 없다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", profileImageKey = tmpKey(otherId))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))
    }

    @Test
    fun `내 id를 접두로 갖는 다른 유저의 사진 키도 막는다`() {
        // 소유 검증에서 후행 슬래시가 빠지면 id=1이 id=11의 파일을 씀
        val lookalike = tmpKey("${ownerId}9".toLong())

        mockMvc
            .perform(postPet(ownerToken, petJson(name = "초코", profileImageKey = lookalike)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))
    }

    @Test
    fun `검증에 실패하면 사진을 승격하지 않는다`() {
        // 승격이 검증보다 앞서면 400에도 복사본이 남고, 정식 접두엔 만료 규칙이 없어 회수 불가
        val before = promotedKeyCount()

        mockMvc
            .perform(
                postPet(
                    ownerToken,
                    petJson(name = "초코", weight = "200.1", profileImageKey = tmpKey(ownerId)),
                ),
            ).andExpect(status().isBadRequest)

        assertEquals(before, promotedKeyCount(), "거절된 요청은 승격을 남기지 않아야 한다")
    }

    @Test
    fun `온보딩 미완료 계정은 펫을 등록하거나 조회할 수 없다`() {
        val pendingToken = mintAccessToken(createPendingUser())

        mockMvc
            .perform(postPet(pendingToken, petJson(name = "초코")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))

        mockMvc
            .perform(get("/api/v1/users/me/pets").header(HttpHeaders.AUTHORIZATION, "Bearer $pendingToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))
    }

    @Test
    fun `타인의 펫은 목록에 보이지 않는다`() {
        registerPet(ownerToken, petJson(name = "내펫"))
        registerPet(otherToken, petJson(name = "남의펫"))

        val body = listBody(ownerToken)
        assertEquals(1, (JsonPath.read(body, "$.data.pets") as List<*>).size)
        assertEquals("내펫", JsonPath.read<String>(body, "$.data.pets[0].name"))
    }

    @Test
    fun `펫이 없으면 빈 배열이다`() {
        assertEquals(0, (JsonPath.read(listBody(ownerToken), "$.data.pets") as List<*>).size)
    }

    @Test
    fun `이름이 비었거나 50자를 넘으면 거절한다`() {
        mockMvc
            .perform(postPet(ownerToken, petJson(name = "  ")))
            .andExpect(status().isBadRequest)

        mockMvc
            .perform(postPet(ownerToken, petJson(name = "가".repeat(51))))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `인증 없이 부르면 401이다`() {
        mockMvc.perform(get("/api/v1/users/me/pets")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `수정하면 값이 바뀌고 version이 올라간다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코", weight = "4.5"))
        val before = JsonPath.read<Int>(listBody(ownerToken), "$.data.pets[0].version")

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초콜릿", weight = "5.2")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("초콜릿"))
            .andExpect(jsonPath("$.data.weight").value(5.2))

        assertTrue(
            JsonPath.read<Int>(listBody(ownerToken), "$.data.pets[0].version") > before,
            "수정하면 낙관적 락 버전이 올라가야 한다",
        )
    }

    @Test
    fun `전체 객체 전송이라 사진 키를 빼면 사진이 지워진다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코", profileImageKey = "tmp/$ownerId/pets/profile/abc.png"))

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileImageKey").value(null as String?))
    }

    @Test
    fun `이미 저장된 정식 키를 다시 보내면 승격하지 않고 그대로 유지한다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코", profileImageKey = "tmp/$ownerId/pets/profile/abc.png"))
        val storedKey = JsonPath.read<String>(listBody(ownerToken), "$.data.pets[0].profileImageKey")
        val promotedBefore = promotedKeyCount()

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코", profileImageKey = storedKey)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileImageKey").value(storedKey))

        assertEquals(promotedBefore, promotedKeyCount(), "정식 키는 다시 승격하지 않아야 한다")
    }

    @Test
    fun `다른 용도의 정식 키는 수정에 쓸 수 없다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코"))

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코", profileImageKey = "users/profile/abc.png")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_PURPOSE_MISMATCH.code))
    }

    @Test
    fun `수정 응답의 version을 그대로 다시 써도 통한다`() {
        // 응답을 flush 전 엔티티로 조립하면 옛 version이 실려 클라이언트가 항상 409를 맞음.
        // 목록 재조회로는 안 잡힘. 응답에 실린 값을 그대로 되먹여야 드러남.
        val petId = registerPet(ownerToken, petJson(name = "초코"))
        val v0 = versionOf(ownerToken, petId)

        val firstBody =
            mockMvc
                .perform(putPet(ownerToken, petId, updateJson(name = "초콜릿", version = v0)))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        val returned = JsonPath.read<Int>(firstBody, "$.data.version")

        assertTrue(returned > v0, "수정 응답은 증가한 version을 실어야 한다 (요청 $v0, 응답 $returned)")
        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코", version = returned)))
            .andExpect(status().isOk)
    }

    @Test
    fun `낡은 version으로 수정하면 409다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코"))
        val current = JsonPath.read<Int>(listBody(ownerToken), "$.data.pets[0].version")

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초콜릿", version = current - 1)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_VERSION_CONFLICT.code))
    }

    @Test
    fun `이미 기본인 펫을 다시 기본으로 지정해도 성공한다`() {
        // 더블탭으로 바로 닿는 경로라 멱등이어야 함
        val petId = registerPet(ownerToken, petJson(name = "초코"))

        mockMvc
            .perform(patchDefault(ownerToken, petId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isDefault").value(true))

        val body = listBody(ownerToken)
        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.pets[?(@.isDefault == true)]").size)
    }

    @Test
    fun `타인의 펫은 수정도 삭제도 대표지정도 못 한다`() {
        val petId = registerPet(ownerToken, petJson(name = "내펫"))

        mockMvc
            .perform(putPet(otherToken, petId, updateJson(name = "탈취")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_ACCESS_DENIED.code))

        mockMvc
            .perform(deletePet(otherToken, petId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_ACCESS_DENIED.code))

        mockMvc
            .perform(patchDefault(otherToken, petId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_ACCESS_DENIED.code))
    }

    @Test
    fun `없는 펫을 건드리면 404다`() {
        mockMvc
            .perform(deletePet(ownerToken, 999_999L))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_NOT_FOUND.code))
    }

    @Test
    fun `수정으로도 종이 다른 품종을 넣을 수 없다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코"))

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코", breedId = catBreedId)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.INVALID_BREED.code))
    }

    @Test
    fun `수정으로도 미래 생년월은 넣을 수 없다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코"))
        val nextYear =
            java.time.YearMonth
                .now()
                .plusYears(1)

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "초코", birthYearMonth = nextYear.toString())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.INVALID_PET_BIRTH_YEAR_MONTH.code))
    }

    @Test
    fun `삭제한 펫은 더 이상 수정할 수 없다`() {
        val petId = registerPet(ownerToken, petJson(name = "초코"))
        mockMvc.perform(deletePet(ownerToken, petId)).andExpect(status().isOk)

        mockMvc
            .perform(putPet(ownerToken, petId, updateJson(name = "부활")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_NOT_FOUND.code))
    }

    @Test
    fun `대표 펫을 삭제하면 남은 펫이 승격되고 그 id를 돌려준다`() {
        val defaultPetId = registerPet(ownerToken, petJson(name = "대표"))
        val otherPetId = registerPet(ownerToken, petJson(name = "둘째"))

        mockMvc
            .perform(deletePet(ownerToken, defaultPetId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.promotedDefaultPetId").value(otherPetId))

        val body = listBody(ownerToken)
        assertEquals(1, (JsonPath.read(body, "$.data.pets") as List<*>).size, "삭제된 펫은 목록에 없어야 한다")
        assertTrue(JsonPath.read<Boolean>(body, "$.data.pets[0].isDefault"), "남은 펫이 대표여야 한다")
    }

    @Test
    fun `비대표를 삭제하면 승격이 없다`() {
        registerPet(ownerToken, petJson(name = "대표"))
        val secondId = registerPet(ownerToken, petJson(name = "둘째"))

        mockMvc
            .perform(deletePet(ownerToken, secondId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.promotedDefaultPetId").value(null as Long?))
    }

    @Test
    fun `마지막 펫을 삭제하면 승격 대상이 없다`() {
        val onlyId = registerPet(ownerToken, petJson(name = "하나뿐"))

        mockMvc
            .perform(deletePet(ownerToken, onlyId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.promotedDefaultPetId").value(null as Long?))

        assertEquals(0, (JsonPath.read(listBody(ownerToken), "$.data.pets") as List<*>).size)
    }

    @Test
    fun `삭제한 펫의 자리는 한도에서 빠진다`() {
        val ids = (0 until 10).map { registerPet(ownerToken, petJson(name = "펫$it")) }
        mockMvc.perform(postPet(ownerToken, petJson(name = "열한번째"))).andExpect(status().isUnprocessableEntity)

        mockMvc.perform(deletePet(ownerToken, ids.last())).andExpect(status().isOk)

        mockMvc.perform(postPet(ownerToken, petJson(name = "새펫"))).andExpect(status().isCreated)
    }

    @Test
    fun `대표를 전환하면 이전 대표가 풀리고 목록 최상단이 바뀐다`() {
        registerPet(ownerToken, petJson(name = "첫째"))
        val secondId = registerPet(ownerToken, petJson(name = "둘째"))

        mockMvc
            .perform(patchDefault(ownerToken, secondId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.petId").value(secondId))
            .andExpect(jsonPath("$.data.isDefault").value(true))

        val body = listBody(ownerToken)
        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.pets[?(@.isDefault == true)]").size)
        assertEquals("둘째", JsonPath.read<String>(body, "$.data.pets[0].name"))
    }

    @Test
    fun `대표에서 풀린 펫도 version이 올라간다`() {
        // 안 올라가면 낡은 화면이 수정 요청으로 기본 펫을 되찾아옴.
        val firstId = registerPet(ownerToken, petJson(name = "첫째"))
        val secondId = registerPet(ownerToken, petJson(name = "둘째"))
        val versionBefore = versionOf(ownerToken, firstId)

        mockMvc.perform(patchDefault(ownerToken, secondId)).andExpect(status().isOk)

        assertTrue(
            versionOf(ownerToken, firstId) > versionBefore,
            "대표에서 풀린 펫의 version이 올라가야 한다",
        )
    }

    @Test
    fun `대표를 잃은 뒤 낡은 version으로 수정하면 409다`() {
        val firstId = registerPet(ownerToken, petJson(name = "첫째"))
        val secondId = registerPet(ownerToken, petJson(name = "둘째"))
        val staleVersion = versionOf(ownerToken, firstId) // 화면이 이 값을 들고 있는 상태

        mockMvc.perform(patchDefault(ownerToken, secondId)).andExpect(status().isOk)

        mockMvc
            .perform(putPet(ownerToken, firstId, updateJson(name = "첫째", isDefault = true, version = staleVersion)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.PET_PROFILE_VERSION_CONFLICT.code))
    }

    /**
     * FE 스키마를 손으로 맞추므로 필드명이 어긋나도 양쪽 테스트가 초록불이고 실서버에서야 드러남.
     * 기대값도 손으로 옮겨 적은 상수라 FE 스키마 자체가 틀린 경우는 못 잡음.
     */
    @Nested
    inner class ContractSnapshot {
        @Test
        fun `등록 201 응답의 필드 집합이 계약과 같다`() {
            val body =
                mockMvc
                    .perform(
                        postPet(
                            ownerToken,
                            petJson(name = "초코", birthYearMonth = "2022-04", weight = "4.5"),
                        ),
                    ).andExpect(status().isCreated)
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)

            assertEquals(listOf("data"), keysOf(body, "$"), "성공 응답은 data 봉투 하나만 갖는다")
            assertEquals(REGISTER_RESPONSE_FIELDS, keysOf(body, "$.data"))
        }

        @Test
        fun `목록 200 응답의 필드 집합이 계약과 같다`() {
            registerPet(ownerToken, petJson(name = "초코"))

            val body = listBody(ownerToken)

            assertEquals(listOf("data"), keysOf(body, "$"))
            assertEquals(listOf("pets"), keysOf(body, "$.data"), "목록 봉투 안에는 pets만 — totalCount 없음")
            assertEquals(LIST_ITEM_FIELDS, keysOf(body, "$.data.pets[0]"))
        }

        @Test
        fun `선택 필드를 안 보내도 응답에서 키가 사라지지 않는다`() {
            // 값이 null이어도 키는 남아야 함. 클라이언트가 '필드 부재'와 'null'을 구분하지 않게
            val body =
                mockMvc
                    .perform(postPet(ownerToken, petJson(name = "초코")))
                    .andExpect(status().isCreated)
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)

            assertEquals(REGISTER_RESPONSE_FIELDS, keysOf(body, "$.data"))
        }

        private fun keysOf(
            body: String,
            path: String,
        ): List<String> = JsonPath.read<Map<String, Any?>>(body, path).keys.sorted()
    }

    /**
     * 절대 쿼리 수는 요청당 고정 쿼리 때문에 무관한 변경에도 흔들려서 증분만 봄.
     * 통계는 프로퍼티가 아니라 런타임에 켬. properties를 바꾸면 컨텍스트가 갈라져
     * 공유 MySQL 컨테이너가 조기 종료될 수 있음.
     */
    @Nested
    inner class ListQueryCount {
        private lateinit var statistics: Statistics
        private var breedSeq = 0

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
            breedSeq = 0
        }

        @Test
        fun `목록 조회 쿼리 수는 펫 마리 수와 무관하다`() {
            persistPetsWithDistinctBreeds(1)
            val withOnePet = countStatementsOnList()

            persistPetsWithDistinctBreeds(4) // 총 5마리
            val withFivePets = countStatementsOnList()

            assertEquals(
                withOnePet,
                withFivePets,
                "join fetch가 없으면 품종 라벨을 읽으며 마리 수만큼 쿼리가 더 나간다 " +
                    "(1마리=$withOnePet, 5마리=$withFivePets)",
            )
        }

        @Test
        fun `목록 조회는 요청당 한 자릿수 쿼리로 끝난다`() {
            // 증분이 0이어도 요청당 수십 개면 문제라 상한을 느슨하게 걸어 회귀만 잡음
            persistPetsWithDistinctBreeds(5)

            val statements = countStatementsOnList()

            assertTrue(statements in 1..9, "목록 조회 쿼리 수가 예상 범위를 벗어났다: $statements")
        }

        private fun countStatementsOnList(): Long {
            statistics.clear()
            listBody(ownerToken)
            return statistics.prepareStatementCount
        }

        /**
         * 펫마다 다른 품종을 줘야 함. 같은 품종을 공유하면 영속성 컨텍스트가 첫 마리에서 읽은
         * Breed를 재사용해, join fetch를 지워도 증분이 0으로 나옴.
         */
        private fun persistPetsWithDistinctBreeds(count: Int) {
            tx.execute {
                val user = em.find(User::class.java, ownerId)
                repeat(count) {
                    val breed = Breed.of(Species.DOG, "품종-${breedSeq++}")
                    em.persist(breed)
                    em.persist(PetProfile.register(user, breed, "펫$it"))
                }
                em.flush()
            }
        }
    }

    private fun tmpKey(userId: Long): String = "${FileKey.tmpPrefixOf(userId, UploadPurpose.PET_PROFILE)}abc.png"

    /** 필터 표현식은 결과가 배열로 오므로 첫 원소를 꺼냄 */
    private fun versionOf(
        token: String,
        petId: Long,
    ): Int {
        val versions = JsonPath.read<List<*>>(listBody(token), "$.data.pets[?(@.petId == $petId)].version")
        return (versions.first() as Number).toInt()
    }

    private fun postPet(
        token: String,
        body: String,
    ) = post("/api/v1/users/me/pets")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun putPet(
        token: String,
        petId: Long,
        body: String,
    ) = put("/api/v1/users/me/pets/$petId")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun deletePet(
        token: String,
        petId: Long,
    ) = delete("/api/v1/users/me/pets/$petId").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun patchDefault(
        token: String,
        petId: Long,
    ) = patch("/api/v1/users/me/pets/$petId/default").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun updateJson(
        name: String,
        breedId: Long = dogBreedId,
        birthYearMonth: String? = null,
        weight: String? = null,
        profileImageKey: String? = null,
        isDefault: Boolean? = null,
        version: Int? = null,
    ): String =
        """
        {
          "name": "$name",
          "breedId": $breedId,
          "birthYearMonth": ${jsonStr(birthYearMonth)},
          "weight": ${weight ?: "null"},
          "profileImageKey": ${jsonStr(profileImageKey)},
          "isDefault": ${isDefault ?: "null"},
          "version": ${version ?: "null"}
        }
        """.trimIndent()

    private fun registerPet(
        token: String,
        body: String,
    ): Long {
        val response =
            mockMvc
                .perform(postPet(token, body))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return readId(response, "$.data.petId")!!
    }

    private fun listBody(token: String): String =
        mockMvc
            .perform(get("/api/v1/users/me/pets").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun readId(
        body: String,
        path: String,
    ): Long? = JsonPath.read<Any?>(body, path)?.let { (it as Number).toLong() }

    private fun petJson(
        name: String,
        breedId: Long = dogBreedId,
        birthYearMonth: String? = null,
        weight: String? = null,
        profileImageKey: String? = null,
        isDefault: Boolean? = null,
    ): String =
        """
        {
          "name": "$name",
          "breedId": $breedId,
          "birthYearMonth": ${jsonStr(birthYearMonth)},
          "weight": ${weight ?: "null"},
          "profileImageKey": ${jsonStr(profileImageKey)},
          "isDefault": ${isDefault ?: "null"}
        }
        """.trimIndent()

    private fun jsonStr(value: String?): String = if (value == null) "null" else "\"$value\""

    private fun createBreed(
        species: Species,
        label: String,
    ): Long =
        tx.execute {
            val breed = Breed.of(species, label)
            em.persist(breed)
            em.flush()
            breed.id
        }!!

    private fun promotedKeyCount(): Int = (fileStorage as FakeFileStorage).promotedKeys.size

    private fun setCreatedAt(
        petId: Long,
        isoDateTime: String,
    ) {
        tx.execute {
            em
                .createQuery("update PetProfile p set p.createdAt = :at where p.id = :id")
                .setParameter("at", java.time.LocalDateTime.parse(isoDateTime))
                .setParameter("id", petId)
                .executeUpdate()
        }
    }

    private fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun createPendingUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            user.id
        }!!

    private fun mintAccessToken(userId: Long): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, "GENERAL")
                .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    companion object {
        // FE 스키마의 등록 응답과 1:1 (userId는 등록 응답에만)
        private val REGISTER_RESPONSE_FIELDS =
            listOf(
                "birthYearMonth",
                "breedId",
                "breedLabel",
                "isDefault",
                "name",
                "petId",
                "profileImageKey",
                "profileImageUrl",
                "species",
                "status",
                "userId",
                "weight",
            ).sorted()

        // FE 스키마의 펫 프로필과 1:1 (userId 없음, version 있음)
        private val LIST_ITEM_FIELDS =
            listOf(
                "birthYearMonth",
                "breedId",
                "breedLabel",
                "isDefault",
                "name",
                "petId",
                "profileImageKey",
                "profileImageUrl",
                "species",
                "status",
                "version",
                "weight",
            ).sorted()
    }
}
