package com.aechak.api.product.category

import com.aechak.application.product.category.usecase.CategoryUseCase
import com.aechak.application.product.category.usecase.result.CategoryResult
import com.aechak.webcommon.error.GlobalExceptionHandler
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** GET /categories의 응답 계약을 standalone MockMvc로 검증한다. */
class CategoryControllerTest {
    private var stubbedTree: () -> List<CategoryResult> = { emptyList() }

    private val fakeUseCase =
        object : CategoryUseCase {
            override fun getCategoryTree(): List<CategoryResult> = stubbedTree()
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(CategoryController(fakeUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `카테고리를 조회하면 200과 중첩 트리를 반환한다`() {
        stubbedTree = {
            listOf(
                CategoryResult(
                    categoryId = 1L,
                    name = "강아지",
                    iconUrl = "icons/dog.png",
                    sortOrder = 1,
                    children =
                        listOf(
                            CategoryResult(
                                categoryId = 11L,
                                name = "사료",
                                iconUrl = null,
                                sortOrder = 1,
                                children = emptyList(),
                            ),
                        ),
                ),
            )
        }

        mockMvc
            .perform(get("/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].categoryId").value(1))
            .andExpect(jsonPath("$.data[0].name").value("강아지"))
            .andExpect(jsonPath("$.data[0].iconUrl").value("icons/dog.png"))
            .andExpect(jsonPath("$.data[0].sortOrder").value(1))
            .andExpect(jsonPath("$.data[0].children[0].categoryId").value(11))
            .andExpect(jsonPath("$.data[0].children[0].name").value("사료"))
            .andExpect(jsonPath("$.data[0].children[0].children").isArray)
            .andExpect(jsonPath("$.data[0].children[0].children").isEmpty)
    }

    @Test
    fun `트리가 비어 있으면 200과 빈 배열을 반환한다`() {
        stubbedTree = { emptyList() }

        mockMvc
            .perform(get("/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `iconUrl이 없는 카테고리는 iconUrl을 null로 반환한다`() {
        stubbedTree = {
            listOf(
                CategoryResult(
                    categoryId = 3L,
                    name = "공통",
                    iconUrl = null,
                    sortOrder = 1,
                    children = emptyList(),
                ),
            )
        }

        mockMvc
            .perform(get("/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].iconUrl").value(nullValue()))
    }
}
