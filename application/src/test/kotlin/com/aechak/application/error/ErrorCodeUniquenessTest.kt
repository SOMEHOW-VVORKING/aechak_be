package com.aechak.application.error

import com.aechak.common.error.ErrorCode
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 에러 코드 숫자가 전 도메인에서 유일한지 본다.
 *
 * `ErrorCode.code` 주석이 그 값을 "클라이언트 분기 기준" 으로 못박고 있다. 숫자가 겹치면
 * 클라이언트가 두 상황을 가를 수 없는데, 그 유일성을 지키는 장치가 없었다.
 *
 * - 자동 병합이 통과시킨다. 두 PR 이 같은 숫자를 서로 다른 줄에 더하면 충돌이 나지 않는다.
 * - Kotlin enum 이 허용한다. 같은 생성자 인자를 쓰는 항목 둘은 문법상 정상이다.
 * - 컴파일도 통과한다.
 *
 * 2026-11-07 에 `#211` 이 develop 과 합쳐지며 `ProductErrorCode` 의 `40008` 이 둘이 됐다. 사람이
 * 파일을 읽다 눈으로 찾았고 머지 직전이라 하마터면 그대로 들어갔다. HTTP 상태로 가르는 우회는
 * 쓸 수 없다 — 같은 열거에 409 가 셋이다.
 *
 * 이 테스트를 application 에 둔 이유: 그 모듈이 domain 과 common 을 의존하고 자기 안에도 열거
 * 둘(AuthErrorCode·FileErrorCode)이 있어 전부 보이는 유일한 자리다.
 */
class ErrorCodeUniquenessTest {
    @Test
    fun `에러 코드 숫자는 전 도메인에서 유일하다`() {
        val collisions = allErrorCodes().groupBy { it.code }.filterValues { it.size > 1 }

        assertTrue(
            collisions.isEmpty(),
            "같은 숫자를 쓰는 항목이 있으면 클라이언트가 그 둘을 가를 수 없다. " +
                "HTTP 상태로 가르는 우회는 같은 상태를 쓰는 코드가 여럿이라 통하지 않는다.\n" +
                collisions.entries.joinToString("\n") { (code, entries) ->
                    "  $code — " + entries.joinToString(", ") { describe(it) }
                },
        )
    }

    /**
     * 스캔이 비어도 "중복 0건"으로 통과해 버리는 것을 막는다.
     *
     * 이 단언이 없으면 스캐너가 망가진 날 위 테스트가 조용히 무력해진다 — 있으나 없으나 같아진다.
     * 하한만 두지 않고 세 모듈에서 각각 하나 이상 찾았는지도 본다. 모듈 하나가 클래스패스에서
     * 빠지면 하한은 넘어도 그 모듈의 코드는 검사되지 않기 때문이다.
     */
    @Test
    fun `스캔이 세 모듈의 열거를 모두 찾는다`() {
        val enums = errorCodeEnums()
        val names = enums.map { it.simpleName }.toSet()

        assertTrue(
            enums.size >= MIN_ENUMS,
            "에러 코드 열거를 ${enums.size}개만 찾았다(하한 $MIN_ENUMS). " +
                "스캐너가 망가지면 중복 검사가 조용히 무력해진다. 찾은 것: $names",
        )

        // common·domain·application 각각의 대표 하나. 스캔이 세 모듈에 닿았다는 증거다.
        for (expected in listOf("CommonErrorCode", "ProductErrorCode", "AuthErrorCode")) {
            assertTrue(
                expected in names,
                "$expected 을 찾지 못했다. 그 모듈이 클래스패스에서 빠지면 하한을 넘어도 " +
                    "그 모듈의 코드는 검사되지 않는다. 찾은 것: $names",
            )
        }

        val codes = allErrorCodes()
        assertTrue(
            codes.size >= MIN_CODES,
            "열거는 ${enums.size}개 찾았는데 항목이 ${codes.size}건뿐이다(하한 $MIN_CODES). " +
                "상수 수집이 망가진 것이다",
        )
    }

    private fun describe(code: ErrorCode): String = "${code.javaClass.simpleName}.${(code as Enum<*>).name}(status=${code.status})"

    private fun allErrorCodes(): List<ErrorCode> = errorCodeEnums().flatMap { type -> type.enumConstants.orEmpty().map { it as ErrorCode } }

    private fun errorCodeEnums(): List<Class<*>> {
        // useDefaultFilters=false: @Component 가 붙지 않은 열거까지 찾는다
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AssignableTypeFilter(ErrorCode::class.java))
        return scanner
            .findCandidateComponents(BASE_PACKAGE)
            .mapNotNull { runCatching { Class.forName(it.beanClassName) }.getOrNull() }
            .filter { it.isEnum }
            .sortedBy { it.name }
    }

    companion object {
        private const val BASE_PACKAGE = "com.aechak"

        /**
         * 하한은 현재 수(2026-11-07 기준 열거 14개, 287건)보다 넉넉히 낮게 둔다. 열거가 늘 때마다
         * 이 숫자를 올리게 만들면 본래 목적과 무관한 수정이 PR 마다 붙는다. 목적은 "스캔이 통째로
         * 비었다"를 잡는 것이지 정확한 개수를 고정하는 것이 아니다.
         */
        private const val MIN_ENUMS = 10
        private const val MIN_CODES = 150
    }
}
