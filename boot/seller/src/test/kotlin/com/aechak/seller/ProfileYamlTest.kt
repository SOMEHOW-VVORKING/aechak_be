package com.aechak.seller

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [단위] 프로파일 설정 파일이 파싱되는지 본다.
 *
 * 통합 테스트는 활성 프로파일을 지정하지 않아 공통 파일만 읽는다. 그래서 dev·local 전용
 * 파일의 문법 오류는 CI를 전부 통과한 뒤 배포에서야 드러난다.
 *
 * 특히 같은 레벨에 같은 키를 두 번 적는 실수가 그렇다. 스프링의 로더는 중복 키를 거부하므로
 * 그 프로파일이 활성화되는 순간 파싱 단계에서 부팅이 깨진다. 설정 블록을 손으로 덧붙이다
 * 흔히 생기는 모양이라 여기서 먼저 잡는다.
 */
class ProfileYamlTest {
    @Test
    fun `프로파일 설정 파일이 중복 키 없이 파싱된다`() {
        val resources = Path.of("src/main/resources")
        val files =
            Files
                .list(resources)
                .use { stream ->
                    stream.filter { it.fileName.toString().matches(Regex("application(-.+)?\\.ya?ml")) }.toList()
                }

        assertTrue(files.isNotEmpty(), "설정 파일을 하나도 찾지 못했다 — 경로 규약이 바뀐 것인지 확인할 것")

        files.forEach { path ->
            val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
            runCatching { Yaml(options).loadAll(Files.readString(path)).forEach { } }
                .onFailure { fail("${path.fileName} 파싱 실패 — 그 프로파일로 뜨면 부팅이 깨진다: ${it.message}") }
        }
    }
}
