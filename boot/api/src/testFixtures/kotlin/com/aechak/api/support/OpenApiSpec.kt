package com.aechak.api.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * 생성 문서 원문을 스키마 표로 접는다. 원문은 파일로도 남긴다 —
 * 문서를 다시 보려고 앱을 따로 띄울 필요를 없앤다.
 */
class OpenApiSpec private constructor(
    val schemas: DocumentedSchemas,
    /** "POST /api/v1/orders/{id}/confirm" -> 문서가 적은 응답 코드들 */
    val operationResponseCodes: Map<String, Set<String>>,
    private val schemaNode: JsonNode,
) {
    fun propertyNames(schema: String): Set<String> = schemas[schema]?.keys ?: emptySet()

    fun allowedValues(
        schema: String,
        property: String,
    ): Set<String> = schemas[schema]?.get(property) ?: emptySet()

    /** 프로퍼티의 JSON 타입 집합. 널 허용이면 "null"이 함께 들어 있다. */
    fun types(
        schema: String,
        property: String,
    ): Set<String> {
        val type =
            schemaNode
                .path(schema)
                .path("properties")
                .path(property)
                .path("type")
        return if (type.isArray) type.mapNotNull { it.stringValue() }.toSet() else setOfNotNull(type.stringValue())
    }

    companion object {
        const val API_DOCS_PATH = "/v3/api-docs"
        private const val REF = "\$ref"
        private const val COMPONENTS_PREFIX = "#/components/schemas/"
        private const val MAX_DEPTH = 5

        fun of(
            raw: String,
            objectMapper: ObjectMapper,
            dumpPath: Path,
        ): OpenApiSpec {
            Files.createDirectories(dumpPath.toAbsolutePath().parent)
            Files.writeString(dumpPath, raw)
            val root = objectMapper.readTree(raw)
            val schemaNode = root.path("components").path("schemas")
            val operations =
                root
                    .path("paths")
                    .properties()
                    .flatMap { (path, verbs) ->
                        verbs.properties().map { (verb, operation) ->
                            "${verb.uppercase()} $path" to operation.path("responses").propertyNames().toSet()
                        }
                    }.toMap()
            return OpenApiSpec(
                schemaNode.properties().associate { (name, node) ->
                    name to
                        node.path("properties").properties().associate { (property, definition) ->
                            property to allowedValuesOf(definition, schemaNode)
                        }
                },
                operations,
                schemaNode,
            )
        }

        /** 허용값은 인라인 enum·배열 items·널 허용 래퍼(oneOf 등)·components 참조 중 어디로든 나온다. */
        private fun allowedValuesOf(
            definition: JsonNode,
            schemas: JsonNode,
            depth: Int = 0,
        ): Set<String> {
            if (depth > MAX_DEPTH) return emptySet()
            definition.get("enum")?.let { values -> return values.mapNotNull { it.stringValue() }.toSet() }
            definition.get("items")?.let { items ->
                allowedValuesOf(items, schemas, depth + 1).takeIf { it.isNotEmpty() }?.let { return it }
            }
            for (key in listOf("oneOf", "anyOf", "allOf")) {
                definition.path(key).forEach { child ->
                    allowedValuesOf(child, schemas, depth + 1).takeIf { it.isNotEmpty() }?.let { return it }
                }
            }
            val ref = definition.get(REF)?.stringValue() ?: return emptySet()
            if (!ref.startsWith(COMPONENTS_PREFIX)) return emptySet()
            return allowedValuesOf(schemas.path(ref.removePrefix(COMPONENTS_PREFIX)), schemas, depth + 1)
        }
    }
}
