package com.nitronbox.app.ui.chat.components

import java.security.MessageDigest

/** Horizontal alignment for a table column, parsed from the separator row. */
enum class Align { LEFT, CENTER, RIGHT }

/**
 * Stable, render-ready block model. Every block carries a [stableId] that is deterministic for a
 * given document so Compose list keys survive streaming re-parses (earlier blocks keep their id).
 */
sealed interface MarkdownBlock {
    val stableId: String

    data class Paragraph(val source: String, override val stableId: String) : MarkdownBlock
    data class Heading(val level: Int, val source: String, override val stableId: String) : MarkdownBlock
    data class Quote(val depth: Int, val blocks: List<MarkdownBlock>, override val stableId: String) : MarkdownBlock
    data class Code(val language: String?, val source: String, override val stableId: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<Align>,
        val rows: List<List<String>>,
        override val stableId: String,
    ) : MarkdownBlock
    data class Math(val source: String, override val stableId: String) : MarkdownBlock
    data class Chart(val language: String, val source: String, override val stableId: String) : MarkdownBlock
    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<ListItem>,
        override val stableId: String,
    ) : MarkdownBlock
    data class Image(val url: String, val alt: String, override val stableId: String) : MarkdownBlock
    data class Divider(override val stableId: String) : MarkdownBlock
}

/** A single list item. [children] holds nested blocks (usually nested lists) rendered under it. */
data class ListItem(
    val text: String,
    val checked: Boolean?,
    val children: List<MarkdownBlock>,
)

object MarkdownParser {
    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.replace("\r\n", "\n").replace("\t", "    ").split('\n')
        return parseBlocks(lines, counter = intArrayOf(0))
    }

    private fun parseBlocks(lines: List<String>, counter: IntArray): List<MarkdownBlock> {
        val output = mutableListOf<MarkdownBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                line.startsWith("```") || line.startsWith("~~~") -> {
                    val fence = line.take(3)
                    val info = line.drop(3).trim()
                    val language = info.substringBefore(' ').substringBefore(':').ifBlank { null }
                    val content = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith(fence)) content += lines[i++]
                    i++ // consume closing fence
                    val source = content.joinToString("\n")
                    output += if (language != null && language.lowercase() in CHART_LANGUAGES) {
                        MarkdownBlock.Chart(language, source, id(counter, "chart"))
                    } else {
                        MarkdownBlock.Code(language, source, id(counter, "code"))
                    }
                }
                line.trim() == "$$" -> {
                    val content = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim() != "$$") content += lines[i++]
                    i++
                    output += MarkdownBlock.Math(content.joinToString("\n"), id(counter, "math"))
                }
                HEADING.matches(line) -> {
                    val marks = line.takeWhile { it == '#' }
                    val text = line.drop(marks.length).trim().trimEnd('#').trim()
                    output += MarkdownBlock.Heading(marks.length.coerceAtMost(6), text, id(counter, "h"))
                    i++
                }
                line.matches(THEMATIC_BREAK) -> {
                    output += MarkdownBlock.Divider(id(counter, "hr"))
                    i++
                }
                isTableStart(lines, i) -> {
                    val headers = splitRow(lines[i])
                    val alignments = separatorAlignments(lines[i + 1])
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        rows += splitRow(lines[i])
                        i++
                    }
                    output += MarkdownBlock.Table(headers, alignments, rows, id(counter, "table"))
                }
                line.trimStart().startsWith('>') -> {
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith('>')) {
                        quote += lines[i].trimStart().drop(1).removePrefix(" ")
                        i++
                    }
                    val depth = lines.first { it.trimStart().startsWith('>') }.trimStart().takeWhile { it == '>' }.length
                    output += MarkdownBlock.Quote(depth.coerceAtMost(4), parseBlocks(quote, counter), id(counter, "quote"))
                }
                isImageLine(line) -> {
                    val (url, alt) = parseImage(line)!!
                    output += MarkdownBlock.Image(url, alt, id(counter, "img"))
                    i++
                }
                LIST_MARKER.containsMatchIn(line) -> {
                    val block = parseList(lines, i, counter)
                    output += block.block
                    i = block.nextIndex
                }
                else -> {
                    val paragraph = mutableListOf(line)
                    i++
                    while (i < lines.size && lines[i].isNotBlank() && !isBlockStart(lines, i)) {
                        paragraph += lines[i++]
                    }
                    output += MarkdownBlock.Paragraph(paragraph.joinToString("\n").trim(), id(counter, "p"))
                }
            }
        }
        return output
    }

    private fun parseList(lines: List<String>, from: Int, counter: IntArray): BlockWithIndex {
        val firstMatch = LIST_MARKER.find(lines[from])!!
        val baseIndent = lines[from].length - lines[from].trimStart().length
        val ordered = firstMatch.groupValues[2].isNotEmpty()
        val startNumber = firstMatch.groupValues[2].trimEnd('.', ')').toIntOrNull() ?: 1
        val items = mutableListOf<ListItem>()
        var i = from
        while (i < lines.size) {
            val line = lines[i]
            val indent = line.length - line.trimStart().length
            val match = LIST_MARKER.find(line)
            val isItemAtLevel = match != null && indent <= baseIndent + 1 &&
                (ordered == (match.groupValues[2].isNotEmpty()))
            if (i > from && !isItemAtLevel) {
                // A blank line then a non-list, non-indented line ends the list.
                if (line.isBlank()) {
                    val next = lines.getOrNull(i + 1)
                    if (next == null || (next.length - next.trimStart().length <= baseIndent &&
                            !LIST_MARKER.containsMatchIn(next))
                    ) break
                    i++
                    continue
                }
                if (indent <= baseIndent) break
            }
            if (!isItemAtLevel) { i++; continue }
            val markerEnd = match!!.range.last + 1
            var rest = line.substring(markerEnd).trim()
            var checked: Boolean? = null
            TASK_CHECK.find(rest)?.let { tm ->
                checked = tm.groupValues[1].lowercase() == "x"
                rest = rest.substring(tm.range.last + 1).trim()
            }
            val itemText = mutableListOf(rest)
            val nested = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val cont = lines[i]
                val contIndent = cont.length - cont.trimStart().length
                if (cont.isBlank()) {
                    // Keep blank only if the list continues after it.
                    if (lines.getOrNull(i + 1)?.let { (it.length - it.trimStart().length) > baseIndent } == true) {
                        nested += ""
                        i++
                        continue
                    }
                    break
                }
                if (LIST_MARKER.containsMatchIn(cont) && contIndent <= baseIndent + 1) break
                if (contIndent > baseIndent) {
                    nested += cont.substring(minOf(baseIndent + 2, contIndent))
                    i++
                } else {
                    itemText += cont.trim()
                    i++
                }
            }
            val children = if (nested.any { it.isNotBlank() }) parseBlocks(nested, counter) else emptyList()
            items += ListItem(itemText.joinToString("\n").trim(), checked, children)
        }
        val block = MarkdownBlock.ListBlock(ordered, startNumber, items, id(counter, "list"))
        return BlockWithIndex(block, i)
    }

    private class BlockWithIndex(val block: MarkdownBlock, val nextIndex: Int)

    private fun isBlockStart(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        return line.startsWith("```") || line.startsWith("~~~") || line.trim() == "$$" ||
            HEADING.matches(line) || line.matches(THEMATIC_BREAK) ||
            line.trimStart().startsWith('>') || LIST_MARKER.containsMatchIn(line) ||
            isImageLine(line) || isTableStart(lines, index)
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean =
        index + 1 < lines.size && lines[index].contains('|') && TABLE_SEPARATOR.matches(lines[index + 1])

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun separatorAlignments(line: String): List<Align> = splitRow(line).map { cell ->
        val left = cell.startsWith(':')
        val right = cell.endsWith(':')
        when {
            left && right -> Align.CENTER
            right -> Align.RIGHT
            else -> Align.LEFT
        }
    }

    private fun isImageLine(line: String): Boolean = IMAGE_LINE.matches(line.trim())

    private fun parseImage(line: String): Pair<String, String>? {
        val m = IMAGE_LINE.matchEntire(line.trim()) ?: return null
        return m.groupValues[2].trim() to m.groupValues[1].trim()
    }

    private fun id(counter: IntArray, prefix: String): String {
        val index = counter[0]++
        return "$prefix-$index"
    }

    private val HEADING = Regex("^#{1,6}\\s+.+")
    private val THEMATIC_BREAK = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$")
    private val LIST_MARKER = Regex("^\\s*(?:([-+*])|(\\d{1,9}[.)]))\\s+")
    private val TASK_CHECK = Regex("^\\[( |x|X)]\\s*")
    private val IMAGE_LINE = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)$")
    private val CHART_LANGUAGES = setOf("mermaid", "chart", "vega", "vega-lite")
}

internal fun String.stableHash(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .take(8)
    .joinToString("") { "%02x".format(it) }
