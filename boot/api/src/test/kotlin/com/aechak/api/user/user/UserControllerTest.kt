package com.aechak.api.user.user

import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserAuthorResult
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class UserControllerTest {
    private var capturedConsents: SubmitConsentsCommand? = null
    private var capturedNickname: SetNicknameCommand? = null
    private var capturedProfile: UpdateProfileCommand? = null

    private val activeMe =
        UserMeResult(
            status = UserStatus.ACTIVE,
            nickname = "코코집사",
            profileImageUrl = "https://cdn.aechak.com/users/profile/abc.webp",
            profileImageKey = "users/profile/abc.webp",
            bio = "코코와 삽니다",
            email = "coco@kakao.com",
            phoneNumber = null,
            isPhoneVerified = false,
        )

    private val fakeConsentUseCase =
        object : ConsentUseCase {
            override fun getActiveTerms(): List<TermResult> = error("not used")

            override fun submitConsents(command: SubmitConsentsCommand) {
                capturedConsents = command
            }
        }

    private val fakeUserUseCase =
        object : UserUseCase {
            override fun checkNickname(
                userId: Long,
                nickname: String,
            ): Boolean = nickname != "선점됨"

            override fun setNickname(command: SetNicknameCommand): UserMeResult {
                capturedNickname = command
                return activeMe
            }

            override fun getMe(userId: Long): UserMeResult =
                UserMeResult(
                    status = UserStatus.PENDING_ONBOARDING,
                    nickname = null,
                    profileImageUrl = null,
                    profileImageKey = null,
                    bio = null,
                    email = null,
                    phoneNumber = null,
                    isPhoneVerified = false,
                )

            override fun updateProfile(command: UpdateProfileCommand): UserMeResult {
                capturedProfile = command
                return activeMe
            }

            override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> = error("not used")

            override fun getAuthors(userIds: Collection<Long>): Map<Long, UserAuthorResult> = error("not used")

            override fun registerFromSocial(): Long = error("not used")
        }

    /** standalone MockMvc에는 시큐리티가 없으므로 @AuthenticationPrincipal을 고정 주체로 채운다. */
    private val principalResolver =
        object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == AuthPrincipal::class.java

            override fun resolveArgument(
                parameter: MethodParameter,
                mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest,
                binderFactory: WebDataBinderFactory?,
            ): Any = AuthPrincipal(userId = 7L, role = UserRole.GENERAL)
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(UserController(fakeUserUseCase, fakeConsentUseCase))
            .setCustomArgumentResolvers(principalResolver)
            // 런타임(Boot)과 동일하게 Kotlin 모듈 매퍼 사용 — is-접두 필드명 보존·필수 필드 누락을 파싱 단계에서 거른다
            .setMessageConverters(JacksonJsonHttpMessageConverter(JsonMapper.builder().addModule(kotlinModule()).build()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `닉네임 검사는 200과 available을 반환한다`() {
        mockMvc
            .perform(get("/users/nickname/check").param("nickname", "코코집사"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(true))

        mockMvc
            .perform(get("/users/nickname/check").param("nickname", "선점됨"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(false))
    }

    @Test
    fun `nickname 파라미터가 누락되면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/users/nickname/check"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `닉네임을 설정하면 200과 변경 후 내 정보를 반환하고 인증 주체의 userId로 위임한다`() {
        mockMvc
            .perform(
                put("/users/me/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.nickname").value("코코집사"))
            .andExpect(jsonPath("$.data.isPhoneVerified").value(false))

        val command = requireNotNull(capturedNickname)
        assertEquals(7L, command.userId, "본문이 아닌 인증 주체에서 userId를 얻는다")
        assertEquals("코코집사", command.nickname)
    }

    @Test
    fun `프로필을 수정하면 200과 변경 후 내 정보를 반환하고 인증 주체의 userId로 위임한다`() {
        mockMvc
            .perform(
                put("/users/me/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사","bio":"코코와 삽니다","profileImageKey":"tmp/7/users/profile/a.webp"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("코코집사"))
            .andExpect(jsonPath("$.data.profileImageKey").value("users/profile/abc.webp"))
            .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.aechak.com/users/profile/abc.webp"))

        val command = requireNotNull(capturedProfile)
        assertEquals(7L, command.userId, "본문이 아닌 인증 주체에서 userId를 얻는다")
        assertEquals("코코집사", command.nickname)
        assertEquals("코코와 삽니다", command.bio)
        assertEquals("tmp/7/users/profile/a.webp", command.profileImageKey)
    }

    @Test
    fun `프로필 수정 - null 필드는 제거 의도로 그대로 전달된다`() {
        mockMvc
            .perform(
                put("/users/me/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사","bio":null,"profileImageKey":null}"""),
            ).andExpect(status().isOk)

        val command = requireNotNull(capturedProfile)
        assertEquals(null, command.bio)
        assertEquals(null, command.profileImageKey)
    }

    @Test
    fun `프로필 수정 - bio가 150자를 넘으면 400과 INVALID_REQUEST 코드로 응답한다`() {
        val longBio = "가".repeat(151)
        mockMvc
            .perform(
                put("/users/me/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사","bio":"$longBio","profileImageKey":null}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `프로필 수정 - nickname이 누락되면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                put("/users/me/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bio":"코코와 삽니다","profileImageKey":null}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `내 정보 조회는 200과 PENDING이면 프로필 계열 null을 반환한다`() {
        mockMvc
            .perform(get("/users/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING_ONBOARDING"))
            .andExpect(jsonPath("$.data.nickname").value(null))
            .andExpect(jsonPath("$.data.profileImageUrl").value(null))
            .andExpect(jsonPath("$.data.email").value(null))
    }

    @Test
    fun `동의를 제출하면 204 무본문으로 응답하고 인증 주체의 userId로 위임한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1,"isAgreed":true},{"termId":4,"isAgreed":false}]}"""),
            ).andExpect(status().isNoContent)

        val command = requireNotNull(capturedConsents)
        assertEquals(7L, command.userId, "본문이 아닌 인증 주체에서 userId를 얻는다")
        assertEquals(listOf(1L to true, 4L to false), command.items.map { it.termId to it.isAgreed })
    }

    @Test
    fun `items가 비면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `같은 termId를 중복 제출하면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1,"isAgreed":true},{"termId":1,"isAgreed":false}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `isAgreed가 누락되면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }
}
