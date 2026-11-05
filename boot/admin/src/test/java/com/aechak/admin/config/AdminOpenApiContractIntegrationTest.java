package com.aechak.admin.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.aechak.admin.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 계약 테스트. 어드민 생성 문서의 쿼리 파라미터가 요청 객체 하나로 접히지 않고, 서버가 받는 이름이 전부 문서에 있는지 본다.
 * 접히거나 빠지면 프런트가 파라미터 이름과 허용값을 문서에서 읽지 못하고, 생성 클라이언트가 실제 쿼리 문자열과 다른 모양이 된다.
 */
class AdminOpenApiContractIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String COMPONENTS_PREFIX = "#/components/schemas/";
    private static final String BASE_PACKAGE = "com.aechak.admin";

    @Autowired
    private WebApplicationContext context;

    private JsonNode root;

    @BeforeEach
    void fetchSpec() throws Exception {
        String raw = MockMvcBuilders.webAppContextSetup(context)
                .build()
                .perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        root = MAPPER.readTree(raw);
        assertTrue(root.path("paths").size() > 0, "생성 문서에 경로가 하나도 없다. 검사가 빈 채로 통과한다");
    }

    @Test
    void 쿼리_파라미터가_요청_객체_하나로_접히지_않고_이름마다_문서에_적힌다() {
        JsonNode schemas = root.path("components").path("schemas");
        List<String> collapsed = new ArrayList<>();
        root.path("paths")
                .properties()
                .forEach(path -> path.getValue().properties().forEach(verb -> {
                    for (JsonNode parameter : queryParameters(verb.getValue())) {
                        if (isObjectSchema(parameter.path("schema"), schemas)) {
                            collapsed.add(verb.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey() + " ?"
                                    + parameter.path("name").asString());
                        }
                    }
                }));

        assertTrue(collapsed.isEmpty(), "쿼리 파라미터가 객체 스키마 하나로 접혀 프런트가 개별 이름을 읽지 못하는 자리가 있다: " + collapsed);
    }

    @Test
    void 쿼리_객체가_바인딩하는_이름이_빠짐없이_그_경로의_쿼리_파라미터로_문서에_적힌다() {
        List<String> undocumented = new ArrayList<>();
        int checked = 0;
        for (RequestMappingHandlerMapping mapping :
                context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (var entry : mapping.getHandlerMethods().entrySet()) {
                if (!entry.getValue().getBeanType().getPackageName().startsWith(BASE_PACKAGE)) {
                    continue;
                }
                Set<String> bound = new HashSet<>();
                for (MethodParameter parameter : entry.getValue().getMethodParameters()) {
                    if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                        bound.addAll(Arrays.asList(BeanUtils.getParameterNames(
                                BeanUtils.getResolvableConstructor(parameter.getParameterType()))));
                    }
                }
                if (bound.isEmpty()) {
                    continue;
                }
                for (String path : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                    for (var verb : entry.getKey().getMethodsCondition().getMethods()) {
                        Set<String> documented = new HashSet<>();
                        queryParameters(root.path("paths")
                                        .path(path)
                                        .path(verb.name().toLowerCase(Locale.ROOT)))
                                .forEach(parameter ->
                                        documented.add(parameter.path("name").asString()));
                        bound.stream()
                                .filter(name -> !documented.contains(name))
                                .forEach(name -> undocumented.add(verb.name() + " " + path + " ?" + name));
                        checked++;
                    }
                }
            }
        }

        assertTrue(checked > 0, "쿼리 객체를 받는 핸들러를 하나도 모으지 못했다. 검사가 빈 채로 통과한다");
        assertTrue(undocumented.isEmpty(), "서버는 받는데 문서에 없는 쿼리 파라미터가 있다: " + undocumented);
    }

    private static List<JsonNode> queryParameters(JsonNode operation) {
        List<JsonNode> query = new ArrayList<>();
        for (JsonNode parameter : operation.path("parameters")) {
            if ("query".equals(parameter.path("in").asString())) {
                query.add(parameter);
            }
        }
        return query;
    }

    private static boolean isObjectSchema(JsonNode schema, JsonNode schemas) {
        String ref = schema.path("$ref").asString();
        JsonNode resolved =
                ref.startsWith(COMPONENTS_PREFIX) ? schemas.path(ref.substring(COMPONENTS_PREFIX.length())) : schema;
        return resolved.has("properties");
    }
}
