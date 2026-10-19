package com.aechak.api.review.moderation

import com.aechak.application.review.moderation.port.ProfanityScanResult
import com.aechak.application.review.moderation.port.ProfanityScanner
import org.ahocorasick.trie.Trie
import org.springframework.stereotype.Component
import java.text.Normalizer

/** 아호코라식 기반 금칙어 스캐너 */
@Component
class AhoCorasickProfanityScanner : ProfanityScanner {
    private val bannedTrie: Trie
    private val whitelistTrie: Trie

    init {
        bannedTrie = buildTrie(BANNED_WORD_RESOURCES.flatMap(::loadWords).toSet())
        whitelistTrie = buildTrie(loadWords("/moderation/whitelist.txt"))
    }

    override fun scan(content: String): ProfanityScanResult {
        val canonical = Normalizer.normalize(content, Normalizer.Form.NFC)
        val normalized = normalize(canonical)
        if (normalized.text.isEmpty()) return ProfanityScanResult(hasMatch = false, maskedContent = canonical, matchedRatio = 0.0)

        val whitelistSpans =
            whitelistTrie
                .parseText(normalized.text)
                .filter { normalized.isContiguous(it.start, it.end) }
                .map { it.start..it.end }

        val covered = sortedSetOf<Int>()
        for (emit in bannedTrie.parseText(normalized.text)) {
            val span = emit.start..emit.end
            if (whitelistSpans.any { it.first <= span.first && span.last <= it.last }) continue
            for (i in span) covered.add(i)
        }
        if (covered.isEmpty()) return ProfanityScanResult(hasMatch = false, maskedContent = canonical, matchedRatio = 0.0)

        return ProfanityScanResult(
            hasMatch = true,
            maskedContent = normalized.mask(canonical, covered),
            matchedRatio = covered.size.toDouble() / normalized.text.length,
        )
    }

    // 문자와 숫자만 소문자로 남기고 각 정규화 글자가 온 원문 구간을 기록
    private fun normalize(canonical: String): NormalizedText {
        val text = StringBuilder(canonical.length)
        val startOffset = ArrayList<Int>(canonical.length)
        val endOffset = ArrayList<Int>(canonical.length)
        var i = 0
        while (i < canonical.length) {
            val codePoint = canonical.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            if (Character.isLetterOrDigit(codePoint)) {
                for (ch in Character.toChars(Character.toLowerCase(codePoint))) {
                    text.append(ch)
                    startOffset.add(i)
                    endOffset.add(i + charCount)
                }
            }
            i += charCount
        }
        return NormalizedText(text.toString(), startOffset.toIntArray(), endOffset.toIntArray())
    }

    private fun buildTrie(words: Collection<String>): Trie {
        val builder = Trie.builder()
        words.forEach { builder.addKeyword(it) }
        return builder.build()
    }

    private fun loadWords(resource: String): List<String> =
        (
            javaClass.getResourceAsStream(resource)
                ?: error("금칙어 리소스를 찾을 수 없습니다: $resource")
        ).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { normalize(Normalizer.normalize(it, Normalizer.Form.NFC)).text }
                .filter { it.isNotEmpty() }
                .toList()
        }

    companion object {
        private val BANNED_WORD_RESOURCES =
            listOf(
                "/moderation/banned-words-base.txt",
                "/moderation/banned-words-custom.txt",
            )
    }
}

private class NormalizedText(
    val text: String,
    private val startOffset: IntArray,
    private val endOffset: IntArray,
) {
    fun isContiguous(
        start: Int,
        end: Int,
    ): Boolean {
        for (k in start until end) {
            val adjacent = startOffset[k + 1] == endOffset[k]
            val sameCodePoint = startOffset[k + 1] == startOffset[k]
            if (!adjacent && !sameCodePoint) return false
        }
        return true
    }

    fun mask(
        canonical: String,
        coveredNormalized: Set<Int>,
    ): String {
        val chars = canonical.toCharArray()
        coveredNormalized.forEach { n ->
            for (offset in startOffset[n] until endOffset[n]) chars[offset] = MASK_CHAR
        }
        return String(chars)
    }

    companion object {
        private const val MASK_CHAR = '*'
    }
}
