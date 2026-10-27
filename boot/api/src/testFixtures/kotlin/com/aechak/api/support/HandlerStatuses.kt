package com.aechak.api.support

import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.ParameterizedType

/**
 * 띄운 컨테이너의 핸들러 매핑에서 "VERB 경로" 별 선언 성공 코드와 본문 없는 핸들러를 모은다.
 * 생성 문서의 응답 코드와 맞대 보는 쪽(OpenApiDocContract.statusCodeMismatches)이 소비한다.
 */
object HandlerStatuses {
    class Handlers(
        /** 선언 값이 없으면 null — 문서 생성기가 200으로 가정하는 자리 */
        val declaredStatuses: Map<String, Int?>,
        val bodylessOperations: Set<String>,
    )

    fun of(
        context: ApplicationContext,
        basePackage: String,
    ): Handlers {
        val declared = mutableMapOf<String, Int?>()
        val bodyless = mutableSetOf<String>()
        // 매핑 빈이 둘 이상인 보트가 있어(문서 UI가 자기 것을 더 올린다) 타입으로 전부 모은다.
        context
            .getBeansOfType(RequestMappingHandlerMapping::class.java)
            .values
            .flatMap { it.handlerMethods.entries }
            .forEach { (info, handler) ->
                if (!handler.beanType.packageName.startsWith(basePackage)) return@forEach
                val status =
                    AnnotatedElementUtils
                        .findMergedAnnotation(handler.method, ResponseStatus::class.java)
                        ?.code
                        ?.value()
                val paths = info.pathPatternsCondition?.patternValues.orEmpty()
                val verbs = info.methodsCondition.methods.map { it.name }
                for (path in paths) {
                    for (verb in verbs) {
                        val operation = "$verb $path"
                        declared[operation] = status
                        if (returnsNoBody(handler)) bodyless += operation
                    }
                }
            }
        return Handlers(declared, bodyless)
    }

    private fun returnsNoBody(handler: HandlerMethod): Boolean {
        val method = handler.method
        if (method.returnType == Void.TYPE) return true
        if (method.returnType != ResponseEntity::class.java) return false
        val generic = method.genericReturnType
        return generic is ParameterizedType && generic.actualTypeArguments.firstOrNull() == java.lang.Void::class.java
    }
}
