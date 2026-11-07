package io.github.whywood.deepdeep.runtime.memory

import java.util.Locale
import kotlin.math.ln

internal data class MemoryMeasurement(val chunks: Int, val elapsedMs: Long)

internal object MemorySearch {
    private const val K1 = 1.2
    private const val B = 0.75
    private const val MAX_CHUNK_CHARS = 1_200
    private const val MAX_LIMIT = 25
    private const val SNIPPET_HEAD = 80
    private const val SNIPPET_TAIL = 160

    private val latinToken = Regex("[a-z0-9][a-z0-9_+.#-]*")
    private val LATIN_SEPARATORS = Regex("[_+.#-]+")
    private val headingLine = Regex("^(#{2,4})\\s+(.+?)\\s*$")

    fun chunk(fileName: String, text: String): List<MemoryDoc> = split(fileName, fileName, text)

    fun dailyChunk(day: String, text: String): List<MemoryDoc> =
        split("daily/$day", "daily/$day.md", text)

    fun measure(documents: List<MemoryDoc>): MemoryMeasurement {
        val started = System.nanoTime()
        index(documents)
        return MemoryMeasurement(documents.size, elapsedMs(started))
    }

    fun search(documents: List<MemoryDoc>, query: String, limit: Int): MemorySearchOutcome {
        val started = System.nanoTime()
        val terms = queryTerms(query)
        if (terms.isEmpty() || documents.isEmpty()) {
            return MemorySearchOutcome(emptyList(), documents.size, elapsedMs(started))
        }
        val index = index(documents)
        val phrase = compact(query)
        val hits = index.entries.mapNotNull { entry ->
            var score = 0.0
            var matched = false
            for (term in terms) {
                val frequency = entry.frequencies[term] ?: continue
                matched = true
                val documentFrequency = index.documentFrequency[term] ?: 0
                val idf = ln(1.0 + (index.entries.size - documentFrequency + 0.5) / (documentFrequency + 0.5))
                val normalization = 1.0 - B + B * entry.length / index.averageLength
                score += idf * (frequency * (K1 + 1)) / (frequency + K1 * normalization)
            }
            if (!matched) return@mapNotNull null
            if (phrase.isNotEmpty() && entry.textLower.contains(phrase)) score += 2.0
            val section = entry.document.section
            if (section != null && phrase.isNotEmpty() && compact(section).contains(phrase)) score += 0.8
            MemoryHit(
                label = entry.document.label,
                fileName = entry.document.fileName,
                section = section,
                score = score,
                snippet = snippet(entry.document.text, terms),
            )
        }
        val ordered = hits.sortedWith(
            compareByDescending<MemoryHit> { it.score }.thenBy { it.fileName },
        ).take(limit.coerceIn(1, MAX_LIMIT))
        return MemorySearchOutcome(ordered, documents.size, elapsedMs(started))
    }

    private class IndexedEntry(
        val document: MemoryDoc,
        val textLower: String,
        val frequencies: Map<String, Int>,
        val length: Int,
    )

    private class Index(
        val entries: List<IndexedEntry>,
        val documentFrequency: Map<String, Int>,
        val averageLength: Double,
    )

    private fun index(documents: List<MemoryDoc>): Index {
        val entries = ArrayList<IndexedEntry>(documents.size)
        val documentFrequency = HashMap<String, Int>()
        var totalLength = 0
        for (document in documents) {
            val tokens = tokenize(document.text + "\n" + (document.section ?: ""), forQuery = false)
            val frequencies = HashMap<String, Int>()
            for (token in tokens) frequencies[token] = (frequencies[token] ?: 0) + 1
            for (token in frequencies.keys) documentFrequency[token] = (documentFrequency[token] ?: 0) + 1
            totalLength += tokens.size
            entries += IndexedEntry(document, document.text.lowercase(Locale.ROOT), frequencies, tokens.size)
        }
        val averageLength = if (entries.isEmpty()) 1.0 else totalLength.toDouble() / entries.size
        return Index(entries, documentFrequency, averageLength)
    }

    private fun queryTerms(query: String): List<String> {
        val tokens = tokenize(query, forQuery = true)
        if (tokens.isEmpty()) return emptyList()
        return tokens.distinct()
    }

    private fun tokenize(text: String, forQuery: Boolean): List<String> {
        val lower = text.lowercase(Locale.ROOT)
        val tokens = ArrayList<String>()
        for (match in latinToken.findAll(lower)) {
            val value = match.value
            if (value.length >= 2) tokens += value
            for (part in value.split(LATIN_SEPARATORS)) {
                if (part.length >= 2 && part != value) tokens += part
            }
        }
        var index = 0
        while (index < lower.length) {
            if (!isCjk(lower[index])) {
                index++
                continue
            }
            var end = index
            while (end < lower.length && isCjk(lower[end])) end++
            val run = lower.substring(index, end)
            if (run.length == 1) {
                tokens += run
            } else {
                for (position in 0 until run.length - 1) {
                    tokens += run.substring(position, position + 2)
                }
                if (!forQuery) {
                    for (position in run.indices) tokens += run[position].toString()
                }
            }
            index = end
        }
        return tokens
    }

    private fun snippet(text: String, terms: List<String>): String {
        val normalized = text.replace("\r\n", "\n")
        val lower = normalized.lowercase(Locale.ROOT)
        val candidates = terms.sortedByDescending { it.length }
        var hitIndex = -1
        for (term in candidates) {
            val index = lower.indexOf(term)
            if (index >= 0 && (hitIndex < 0 || index < hitIndex)) hitIndex = index
        }
        if (hitIndex < 0) {
            return if (normalized.length <= SNIPPET_HEAD + SNIPPET_TAIL) normalized
            else normalized.take(SNIPPET_HEAD + SNIPPET_TAIL).trimEnd() + "…"
        }
        var start = (hitIndex - SNIPPET_HEAD).coerceAtLeast(0)
        var end = (hitIndex + SNIPPET_TAIL).coerceAtMost(normalized.length)
        if (start > 0) {
            val newline = normalized.indexOf('\n', hitIndex - SNIPPET_HEAD)
            if (newline in start until hitIndex) start = newline + 1
        }
        if (end < normalized.length) {
            val newline = normalized.lastIndexOf('\n', end)
            if (newline > hitIndex) end = newline
        }
        val part = normalized.substring(start, end).trim()
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < normalized.length) "…" else ""
        return prefix + part.replace(Regex("\\n{2,}"), "\n").replace('\n', ' ').trim() + suffix
    }

    private fun compact(text: String): String = text.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }

    private fun isCjk(value: Char): Boolean =
        value in '\u4e00'..'\u9fff' || value in '\u3400'..'\u4dbf'

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    private fun split(label: String, fileName: String, text: String): List<MemoryDoc> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val documents = ArrayList<MemoryDoc>()
        var heading: String? = null
        val buffer = StringBuilder()
        fun flush() {
            val body = buffer.toString().trim()
            buffer.setLength(0)
            if (body.isEmpty()) return
            if (body.length <= MAX_CHUNK_CHARS) {
                documents += MemoryDoc(label, fileName, heading, body)
                return
            }
            var start = 0
            while (start < body.length) {
                var end = (start + MAX_CHUNK_CHARS).coerceAtMost(body.length)
                if (end < body.length) {
                    val newline = body.lastIndexOf('\n', end)
                    if (newline > start + MAX_CHUNK_CHARS / 2) end = newline
                }
                documents += MemoryDoc(label, fileName, heading, body.substring(start, end).trim())
                start = end
            }
        }
        for (line in trimmed.split('\n')) {
            if (headingLine.matches(line)) {
                flush()
                heading = line.trim()
                continue
            }
            if (line.isBlank()) {
                flush()
                continue
            }
            buffer.append(line).append('\n')
        }
        flush()
        return documents
    }
}
