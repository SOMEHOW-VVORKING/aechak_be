package com.aechak.api.user.term

import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.domain.user.term.enums.TermType
import com.aechak.webcommon.error.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.LocalDateTime

class TermControllerTest {
    private val fakeUseCase =
        object : ConsentUseCase {
            override fun getActiveTerms(): List<TermResult> =
                listOf(
                    TermResult(
                        termId = 1L,
                        type = TermType.SERVICE,
                        isRequired = true,
                        version = "1.0",
                        title = "서비스 이용약관",
                        body = "약관 본문",
                        effectiveAt = LocalDateTime.of(2026, 7, 1, 0, 0),
                    ),
                )

            override fun submitConsents(command: SubmitConsentsCommand) = error("not used")
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TermController(fakeUseCase))
            // 런타임(Boot)과 동일하게 Kotlin 모듈 매퍼 사용 — is-접두 필드명(isRequired) 보존
            .setMessageConverters(JacksonJsonHttpMessageConverter(JsonMapper.builder().addModule(kotlinModule()).build()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `약관을 조회하면 200과 본문 포함 약관 목록을 반환한다`() {
        mockMvc
            .perform(get("/terms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].termId").value(1))
            .andExpect(jsonPath("$.data[0].type").value("SERVICE"))
            .andExpect(jsonPath("$.data[0].isRequired").value(true))
            .andExpect(jsonPath("$.data[0].version").value("1.0"))
            .andExpect(jsonPath("$.data[0].title").value("서비스 이용약관"))
            .andExpect(jsonPath("$.data[0].body").value("약관 본문"))
            .andExpect(jsonPath("$.data[0].effectiveAt").exists())
    }
}
