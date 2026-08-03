package app.nitronbox.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class TextBlock(val text: String, val heading: Int = 0, val quote: Boolean = false) : MdBlock
    data class CodeBlock(val code: String, val language: String) : MdBlock
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            parseBlocks(text).forEach { block ->
                when (block) {
                    is MdBlock.CodeBlock -> Column(
                        Modifier.fillMaxWidth().background(Color(0xB3050912), RoundedCornerShape(14.dp)).padding(12.dp)
                    ) {
                        Text(block.language.ifBlank { "code" }, color = Color(0xFF6E83A6), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            block.code,
                            modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                            color = Color(0xFFD8E7FF), fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                    is MdBlock.TextBlock -> Text(
                        inlineMarkdown(block.text),
                        modifier = Modifier.then(if (block.quote) Modifier.background(Color(0x147A9DD8), RoundedCornerShape(0.dp, 10.dp, 10.dp, 0.dp)).padding(12.dp, 7.dp) else Modifier),
                        color = if (block.heading > 0) Color(0xFFF1F6FF) else Color(0xFFC9D5E9),
                        fontSize = when (block.heading) { 1 -> 22.sp; 2 -> 18.sp; 3 -> 16.sp; else -> 13.sp },
                        lineHeight = when (block.heading) { 1 -> 28.sp; 2 -> 24.sp; else -> 20.sp },
                        fontWeight = if (block.heading > 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun parseBlocks(source: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    var codeLanguage = ""
    val code = mutableListOf<String>()
    var inCode = false
    fun flushText() {
        if (paragraph.isEmpty()) return
        val raw = paragraph.joinToString("\n")
        val hashes = raw.takeWhile { it == '#' }.length.takeIf { raw.getOrNull(it) == ' ' } ?: 0
        result += MdBlock.TextBlock(
            text = when { hashes > 0 -> raw.drop(hashes + 1); raw.startsWith("> ") -> raw.removePrefix("> "); else -> raw },
            heading = hashes.coerceAtMost(3), quote = raw.startsWith("> "),
        )
        paragraph.clear()
    }
    source.lines().forEach { line ->
        if (line.startsWith("```")) {
            if (inCode) { result += MdBlock.CodeBlock(code.joinToString("\n"), codeLanguage); code.clear(); inCode = false }
            else { flushText(); codeLanguage = line.removePrefix("```").trim(); inCode = true }
        } else if (inCode) code += line
        else if (line.isBlank()) flushText()
        else if (line.matches(Regex("^#{1,3}\\s.*")) || line.startsWith("> ")) { flushText(); paragraph += line; flushText() }
        else paragraph += line
    }
    if (inCode) result += MdBlock.CodeBlock(code.joinToString("\n"), codeLanguage)
    flushText()
    return result
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|~~[^~]+~~|\\*[^*]+\\*)")
    var position = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(position, match.range.first))
        val token = match.value
        when {
            token.startsWith("`") -> pushStyle(SpanStyle(color = Color(0xFF9FE5FF), background = Color(0x1F5984C0), fontFamily = FontFamily.Monospace))
            token.startsWith("**") -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFF0F5FF)))
            token.startsWith("~~") -> pushStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
            else -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        append(token.removeSurrounding("`").removeSurrounding("**").removeSurrounding("~~").removeSurrounding("*"))
        pop()
        position = match.range.last + 1
    }
    append(text.substring(position))
}
