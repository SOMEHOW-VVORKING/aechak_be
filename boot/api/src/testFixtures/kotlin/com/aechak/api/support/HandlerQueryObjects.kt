package com.aechak.api.support

import org.springframework.beans.BeanUtils
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 띄운 컨테이너의 핸들러 매핑에서 "VERB 경로" 별로 쿼리 객체(@ModelAttribute)가 바인딩하는 이름을 모은다.
 * 생성 문서의 쿼리 파라미터와 맞대 보는 쪽(OpenApiDocContract.undocumentedQueryObjectFields)이 소비한다.
 */
object HandlerQueryObjects {
    fun of(
        context: ApplicationContext,
        basePackage: String,
    ): Map<String, Set<String>> =
        context
            .getBeansOfType(RequestMappingHandlerMapping::class.java)
            .values
            .flatMap { it.handlerMethods.entries }
            .filter { (_, handler) -> handler.beanType.packageName.startsWith(basePackage) }
            .flatMap { (info, handler) ->
                val bound =
                    handler.methodParameters
                        .filter { it.hasParameterAnnotation(ModelAttribute::class.java) }
                        .flatMap { bindingNames(it.parameterType) }
                        .toSet()
                if (bound.isEmpty()) return@flatMap emptyList()
                val paths = info.pathPatternsCondition?.patternValues.orEmpty()
                val verbs = info.methodsCondition.methods.map { it.name }
                paths.flatMap { path -> verbs.map { verb -> "$verb $path" to bound } }
            }.toMap()

    /** 바인더와 같은 경로로 생성자를 고름. Kotlin 데이터 클래스면 주 생성자, 레코드면 정규 생성자의 파라미터 이름이 쿼리 이름임 */
    private fun bindingNames(type: Class<*>): List<String> =
        BeanUtils.getParameterNames(BeanUtils.getResolvableConstructor(type)).map {
            requireNotNull(it) { "${type.name}의 생성자 파라미터 이름을 읽지 못했다" }
        }
}
