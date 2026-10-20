package com.aechak.api.support

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.TypeFilter

/** 스키마 이름 -> (프로퍼티 이름 -> 문서가 못박은 허용값. 못박지 않았으면 빈 집합). 생성된 문서에서 뽑아 넘긴다. */
typealias DocumentedSchemas = Map<String, Map<String, Set<String>>>

/**
 * 생성된 OpenAPI 문서를 실제 코드와 맞대 보는 검사 도구. 실행 모듈 두 곳의 계약 테스트가 함께 쓴다.
 *
 * 문서는 springdoc이 자체 ObjectMapper로 만들고 응답은 컨테이너의 ObjectMapper가 만든다. 두 매퍼가
 * 다르고, 성공 코드는 애노테이션이 없으면 200으로 가정된다 — 둘이 벌어지는 것은 코드만 읽어서는 보이지 않는다.
 */
object OpenApiDocContract {
    /**
     * 문서에 올라간 스키마 이름 하나를 두 클래스가 나눠 쓰는 자리를 찾는다.
     * 문서 생성기가 단순 이름으로 스키마를 등록하므로 겹치면 한 벌만 남고 나머지가 사라진다.
     */
    fun schemaNameCollisions(
        basePackage: String,
        documentedNames: Set<String>,
        extraDtoPackages: Set<String> = emptySet(),
    ): Map<String, List<String>> =
        dtoClasses(basePackage, extraDtoPackages)
            .groupBy(::schemaNameOf)
            .filterKeys { it in documentedNames }
            .filterValues { it.size > 1 }
            .mapValues { (_, classes) -> classes.map { it.name }.sorted() }

    /**
     * is-접두 불리언이 문서에서 접두를 잃은 자리를 찾는다 — 문서가 접두를 뗀 이름으로 적어 둔 경우만 걸린다.
     * 실제 응답은 접두를 유지한다(JacksonWireFormatTest가 그 직렬화를 고정한다).
     * 문서에서 아예 숨긴 프로퍼티(내부 검증용)는 어긋날 자리가 없으므로 세지 않는다.
     */
    fun isPrefixMismatches(
        basePackage: String,
        schemas: DocumentedSchemas,
        extraDtoPackages: Set<String> = emptySet(),
    ): List<String> =
        dtoClasses(basePackage, extraDtoPackages)
            .flatMap { type ->
                val schemaName = schemaNameOf(type)
                val documented = schemas[schemaName] ?: return@flatMap emptyList()
                isPrefixedBooleanNames(type)
                    .filter { it !in documented.keys && strippedName(it) in documented.keys }
                    .map { "$schemaName.$it (문서에는 '${strippedName(it)}')" }
            }.sorted()

    /**
     * 문서가 적은 성공 코드가 핸들러가 선언한 코드와 다른 자리를 찾는다.
     * 문서 생성기는 @ResponseStatus가 없으면 성공 코드를 200으로 가정한다 — 204·201·302를
     * 돌려주는 핸들러는 문서에만 200으로 적힌다.
     *
     * 본문을 돌려주지 않는 핸들러는 코드를 반드시 명시해야 한다. 그쪽은 200이 기본값으로
     * 성립하지 않고(본문 없는 200은 뜻이 없다), 빠뜨리면 다시 조용히 어긋난다.
     */
    fun statusCodeMismatches(
        documentedResponseCodes: Map<String, Set<String>>,
        declaredStatuses: Map<String, Int?>,
        bodylessOperations: Set<String>,
    ): List<String> {
        require(declaredStatuses.isNotEmpty()) { "핸들러를 하나도 모으지 못했다 — 검사가 빈 채로 통과한다" }
        return declaredStatuses
            .mapNotNull { (operation, declared) ->
                val documented = documentedResponseCodes[operation]
                when {
                    documented == null -> {
                        "$operation — 문서에 없는 엔드포인트"
                    }

                    declared == null && operation in bodylessOperations -> {
                        "$operation — 본문 없는 핸들러인데 @ResponseStatus가 없다(문서는 ${documented.joinToString()})"
                    }

                    documented != setOf((declared ?: DEFAULT_SUCCESS_CODE).toString()) -> {
                        "$operation — 선언 ${declared ?: DEFAULT_SUCCESS_CODE}, 문서 ${documented.joinToString()}"
                    }

                    else -> {
                        null
                    }
                }
            }.sorted()
    }

    /** 상태·클레임 종류인데 허용값이 없는 프로퍼티를 찾는다. */
    fun statusPropertiesWithoutAllowedValues(
        schemas: DocumentedSchemas,
        exemptProperties: Set<String>,
    ): List<String> =
        schemas
            .flatMap { (schema, properties) ->
                properties
                    .filterKeys { it !in exemptProperties && isStatusLike(it) }
                    .filterValues { allowedValues -> allowedValues.isEmpty() }
                    .map { (name, _) -> "$schema.$name" }
            }.sorted()

    private fun strippedName(getterName: String): String = getterName.removePrefix("is").replaceFirstChar(Char::lowercase)

    private fun isStatusLike(name: String): Boolean = name == "status" || name.endsWith("Status") || name == "claimType"

    /** JVM 게터 이름이 그대로 와이어 필드명이 되는 is-접두 불리언 프로퍼티들. */
    private fun isPrefixedBooleanNames(type: Class<*>): List<String> =
        type.declaredMethods
            .filter { it.parameterCount == 0 && IS_PREFIX.matches(it.name) }
            .filter { it.returnType == Boolean::class.javaPrimitiveType || it.returnType == java.lang.Boolean::class.java }
            .map { it.name }
            .distinct()

    private fun schemaNameOf(type: Class<*>): String {
        val schema = type.annotations.firstOrNull { it.annotationClass.qualifiedName == SCHEMA_ANNOTATION } ?: return type.simpleName
        val declared = schema.javaClass.getMethod("name").invoke(schema) as String
        return declared.ifEmpty { type.simpleName }
    }

    /** 요청·응답 DTO만 본다 — 컨트롤러·설정·테스트 보조 클래스까지 긁으면 이름이 우연히 겹친다. */
    private fun dtoClasses(
        basePackage: String,
        extraDtoPackages: Set<String>,
    ): List<Class<*>> {
        val scanner =
            object : ClassPathScanningCandidateComponentProvider(false) {
                override fun isCandidateComponent(definition: AnnotatedBeanDefinition) = true
            }
        scanner.addIncludeFilter(TypeFilter { _, _ -> true })
        val classes =
            scanner
                .findCandidateComponents(basePackage)
                .mapNotNull(BeanDefinition::getBeanClassName)
                .mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
                .filter { type ->
                    val packageName = type.packageName
                    packageName.endsWith(".request") || packageName.endsWith(".response") || packageName in extraDtoPackages
                }
        require(classes.isNotEmpty()) { "$basePackage 아래에서 DTO를 하나도 찾지 못했다 — 검사가 빈 채로 통과한다" }
        return classes
    }

    private const val DEFAULT_SUCCESS_CODE = 200
    private const val SCHEMA_ANNOTATION = "io.swagger.v3.oas.annotations.media.Schema"
    private val IS_PREFIX = Regex("^is[A-Z].*")
}
