package com.aechak.api.product.category

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.category.usecase.CategoryUseCase
import com.aechak.application.product.category.usecase.result.CategoryResult
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 실제 애플리케이션과 영속성 계층을 연결해 카테고리 트리 조립과 노출 정책을 검증한다.
 */
class CategoryUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var categoryUseCase: CategoryUseCase

    private fun persistCategory(
        parent: Category?,
        name: String,
        sortOrder: Int,
    ): Category {
        val depth = if (parent == null) 1 else parent.depth + 1
        val category = Category.create(parent, depth, name, null, sortOrder)
        em.persist(category)
        return category
    }

    private fun overrideCategoryStatus(
        categoryId: Long,
        status: CategoryStatus,
    ) {
        em
            .createQuery("update Category c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", categoryId)
            .executeUpdate()
    }

    private fun collectNames(nodes: List<CategoryResult>): Set<String> =
        nodes.flatMap { listOf(it.name) + collectNames(it.children) }.toSet()

    @Test
    fun `활성 카테고리를 대 중 소 3단계 트리로 조립한다`() {
        tx.executeWithoutResult {
            val root = persistCategory(null, "강아지", 1)
            val mid = persistCategory(root, "사료", 1)
            persistCategory(mid, "건식사료", 1)
        }

        val tree = categoryUseCase.getCategoryTree()

        assertEquals(1, tree.size, "루트(대분류)는 1개여야 한다")
        val root = tree.single()
        assertEquals("강아지", root.name)
        assertEquals(1, root.children.size, "대분류 아래 중분류는 1개여야 한다")
        val mid = root.children.single()
        assertEquals("사료", mid.name)
        assertEquals(listOf("건식사료"), mid.children.map { it.name }, "중분류 아래 소분류까지 중첩돼야 한다")
    }

    @Test
    fun `같은 부모의 자식은 sortOrder 오름차순으로 정렬한다`() {
        tx.executeWithoutResult {
            val root = persistCategory(null, "강아지", 1)
            persistCategory(root, "둘째", 2)
            persistCategory(root, "첫째", 1)
        }

        val children = categoryUseCase.getCategoryTree().single().children
        assertEquals(listOf("첫째", "둘째"), children.map { it.name }, "형제는 sortOrder 오름차순이어야 한다")
    }

    @Test
    fun `루트도 sortOrder 오름차순으로 정렬한다`() {
        tx.executeWithoutResult {
            persistCategory(null, "고양이", 2)
            persistCategory(null, "강아지", 1)
        }

        assertEquals(
            listOf("강아지", "고양이"),
            categoryUseCase.getCategoryTree().map { it.name },
            "루트도 sortOrder 오름차순이어야 한다",
        )
    }

    @Test
    fun `같은 부모의 자식은 sortOrder가 같으면 id 오름차순으로 정렬한다`() {
        tx.executeWithoutResult {
            val root = persistCategory(null, "강아지", 1)
            persistCategory(root, "먼저", 1)
            persistCategory(root, "나중", 1)
        }

        val children = categoryUseCase.getCategoryTree().single().children
        assertEquals(listOf("먼저", "나중"), children.map { it.name }, "sortOrder 동일 시 삽입(id) 순서로 안정 정렬돼야 한다")
    }

    @Test
    fun `카테고리가 하나도 없으면 빈 트리를 반환한다`() {
        assertEquals(emptyList<CategoryResult>(), categoryUseCase.getCategoryTree(), "데이터가 없으면 예외가 아닌 빈 목록이어야 한다")
    }

    @Test
    fun `자식이 없는 대분류는 children이 빈 목록으로 반환된다`() {
        tx.executeWithoutResult {
            persistCategory(null, "대분류만", 1)
        }

        val root = categoryUseCase.getCategoryTree().single()
        assertTrue(root.children.isEmpty(), "자식이 없으면 children은 널이 아닌 빈 배열이어야 한다")
    }

    @Test
    fun `비활성이거나 삭제된 루트 카테고리는 트리에서 제외된다`() {
        CategoryStatus.entries
            .filterNot { it == CategoryStatus.ACTIVE }
            .forEach { hiddenStatus ->
                tx.executeWithoutResult {
                    persistCategory(null, "활성-$hiddenStatus", 1)
                    val hidden = persistCategory(null, "숨김-$hiddenStatus", 2)
                    em.flush()
                    overrideCategoryStatus(hidden.id, hiddenStatus)
                }

                val names = categoryUseCase.getCategoryTree().map { it.name }
                assertTrue(names.contains("활성-$hiddenStatus"), "$hiddenStatus 케이스에서 활성 루트는 남아야 한다")
                assertFalse(names.contains("숨김-$hiddenStatus"), "$hiddenStatus 루트는 트리에서 빠져야 한다")
            }
    }

    @Test
    fun `자신이나 상위 조상이 비활성이면 대상 노드는 트리에서 제외된다`() {
        tx.executeWithoutResult {
            val root = persistCategory(null, "정상대", 1)
            val mid = persistCategory(root, "정상중", 1)
            persistCategory(mid, "정상소", 1)
        }
        assertTrue(collectNames(categoryUseCase.getCategoryTree()).contains("정상소"), "체인이 전부 활성이면 소분류가 노출돼야 한다")

        assertLeafExcludedWhenInactive(CategoryTarget.SELF)
        assertLeafExcludedWhenInactive(CategoryTarget.PARENT)
        assertLeafExcludedWhenInactive(CategoryTarget.GRANDPARENT)
    }

    private enum class CategoryTarget { SELF, PARENT, GRANDPARENT }

    private fun assertLeafExcludedWhenInactive(target: CategoryTarget) {
        val leafName = "소분류-$target"
        tx.executeWithoutResult {
            val root = persistCategory(null, "대분류-$target", 1)
            val mid = persistCategory(root, "중분류-$target", 1)
            val leaf = persistCategory(mid, leafName, 1)
            em.flush()
            val targetId =
                when (target) {
                    CategoryTarget.SELF -> leaf.id
                    CategoryTarget.PARENT -> mid.id
                    CategoryTarget.GRANDPARENT -> root.id
                }
            overrideCategoryStatus(targetId, CategoryStatus.INACTIVE)
        }

        val names = collectNames(categoryUseCase.getCategoryTree())
        assertFalse(names.contains(leafName), "$target 비활성 시 소분류가 트리에서 제외돼야 한다")
    }

    @Test
    fun `서로 다른 대분류의 서브트리가 섞이지 않는다`() {
        tx.executeWithoutResult {
            val dog = persistCategory(null, "강아지", 1)
            persistCategory(dog, "강아지사료", 1)
            val cat = persistCategory(null, "고양이", 2)
            persistCategory(cat, "고양이사료", 1)
        }

        val tree = categoryUseCase.getCategoryTree()
        val dog = tree.first { it.name == "강아지" }
        val cat = tree.first { it.name == "고양이" }
        assertEquals(listOf("강아지사료"), dog.children.map { it.name }, "강아지 서브트리에 다른 대분류의 자식이 섞이면 안 된다")
        assertEquals(listOf("고양이사료"), cat.children.map { it.name })
    }
}
