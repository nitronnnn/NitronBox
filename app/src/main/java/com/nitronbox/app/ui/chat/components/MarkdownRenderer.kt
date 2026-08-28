package com.nitronbox.app.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import kotlinx.coroutines.delay

fun interface LatexBlockRenderer {
    @Composable fun Render(source: String, modifier: Modifier)
}

fun interface ChartBlockRenderer {
    @Composable fun Render(language: String, source: String, modifier: Modifier)
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    modifier: Modifier = Modifier,
    latexRenderer: LatexBlockRenderer? = null,
    chartRenderer: ChartBlockRenderer? = null,
    onLinkClick: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            androidx.compose.runtime.key(block.stableId) {
                when (block) {
                    is MarkdownBlock.Paragraph -> InlineMarkdown(block.source, onLinkClick = onLinkClick)
                    is MarkdownBlock.Heading -> HeadingBlock(block)
                    is MarkdownBlock.Quote -> QuoteBlock(block, onLinkClick)
                    is MarkdownBlock.Code -> CodeBlock(block.language, block.source)
                    is MarkdownBlock.Table -> TableBlock(block)
                    is MarkdownBlock.Math -> if (latexRenderer != null) {
                        latexRenderer.Render(block.source, Modifier.fillMaxWidth())
                    } else FallbackRichBlock("LaTeX", block.source)
                    is MarkdownBlock.Chart -> if (chartRenderer != null) {
                        chartRenderer.Render(block.language, block.source, Modifier.fillMaxWidth())
                    } else FallbackRichBlock(block.language.uppercase(), block.source)
                    is MarkdownBlock.ListBlock -> ListBlockView(block, onLinkClick)
                    is MarkdownBlock.Image -> ImageBlock(block)
                    is MarkdownBlock.Divider -> HorizontalDivider(color = NitronTheme.colors.border)
                }
            }
        }
    }
}

/** Recursive block view used by quotes and nested list children. */
@Composable
private fun BlockView(block: MarkdownBlock, onLinkClick: (String) -> Unit) {
    when (block) {
        is MarkdownBlock.Paragraph -> InlineMarkdown(block.source, onLinkClick = onLinkClick)
        is MarkdownBlock.Heading -> Text(
            block.source,
            style = MaterialTheme.typography.titleMedium,
            color = NitronTheme.colors.textPrimary,
        )
        is MarkdownBlock.ListBlock -> ListBlockView(block, onLinkClick)
        is MarkdownBlock.Quote -> QuoteBlock(block, onLinkClick)
        is MarkdownBlock.Code -> CodeBlock(block.language, block.source)
        is MarkdownBlock.Image -> ImageBlock(block)
        else -> FallbackRichBlock("Block", block.stableId)
    }
}

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(block.source, style = style, color = NitronTheme.colors.textPrimary)
}

@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote, onLinkClick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(NitronTheme.colors.accent, NitronTheme.shapes.pill),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.blocks.forEach { child -> BlockView(child, onLinkClick) }
        }
    }
}

@Composable
private fun ListBlockView(block: MarkdownBlock.ListBlock, onLinkClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                val marker = when {
                    item.checked == true -> "☑"
                    item.checked == false -> "☐"
                    block.ordered -> "${block.start + index}."
                    else -> "•"
                }
                Text(
                    marker,
                    color = NitronTheme.colors.accent,
                    modifier = Modifier.width(26.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InlineMarkdown(item.text, onLinkClick = onLinkClick)
                    item.children.forEach { child -> BlockView(child, onLinkClick) }
                }
            }
        }
    }
}

@Composable
private fun ImageBlock(block: MarkdownBlock.Image) {
    AsyncImage(
        model = block.url,
        contentDescription = block.alt.ifBlank { "Markdown image" },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(NitronTheme.shapes.medium),
    )
}

@Composable
private fun InlineMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
    onLinkClick: (String) -> Unit,
) {
    val colors = NitronTheme.colors
    val annotated = remember(source, colors.accent, colors.codeBackground) {
        parseInline(source, colors.accent, colors.codeBackground)
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = NitronTheme.colors.textPrimary,
            fontStyle = if (italic) FontStyle.Italic else null,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        },
    )
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var wrap by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val horizontalScroll = rememberScrollState()
    if (copied) LaunchedEffect(Unit) { delay(1_400); copied = false }

    Column(
        Modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelMedium,
                color = NitronTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { wrap = !wrap }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.WrapText, if (wrap) "Disable wrapping" else "Wrap code", tint = if (wrap) NitronTheme.colors.accent else NitronTheme.colors.textSecondary)
            }
            IconButton(
                onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, if (copied) "Copied" else "Copy code", tint = if (copied) NitronTheme.colors.accent else NitronTheme.colors.textSecondary)
            }
        }
        HorizontalDivider(color = NitronTheme.colors.border.copy(alpha = 0.55f))
        Box(
            Modifier
                .then(if (wrap) Modifier else Modifier.horizontalScroll(horizontalScroll))
                .padding(16.dp),
        ) {
            Text(
                text = remember(code, language) { highlightCode(code, language) },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                softWrap = wrap,
                color = NitronTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun TableBlock(block: MarkdownBlock.Table) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .border(0.8.dp, NitronTheme.colors.border, NitronTheme.shapes.small)
            .padding(6.dp),
    ) {
        TableRow(block.headers, header = true)
        HorizontalDivider(color = NitronTheme.colors.border)
        block.rows.forEach { TableRow(it, header = false) }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(
                text = cell,
                modifier = Modifier.width(150.dp).padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = NitronTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun FallbackRichBlock(label: String, source: String) {
    Column(Modifier.fillMaxWidth().nitronSurface(SurfaceLevel.Raised, NitronTheme.shapes.medium).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NitronTheme.colors.accent)
        Spacer(Modifier.height(6.dp))
        Text(source, style = MaterialTheme.typography.bodyMedium, color = NitronTheme.colors.textPrimary)
    }
}

private fun parseInline(source: String, accent: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    INLINE_TOKEN.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") || token.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.drop(2).dropLast(2)) }
            token.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(token.drop(2).dropLast(2)) }
            token.startsWith('`') -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(token.drop(1).dropLast(1)) }
            token.startsWith('[') -> {
                val label = token.substringAfter('[').substringBefore("](")
                val url = token.substringAfter("](").dropLast(1)
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) { append(label) }
                pop()
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.drop(1).dropLast(1)) }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}

private fun highlightCode(code: String, language: String?): AnnotatedString = buildAnnotatedString {
    append(code)
    val keywords = when (language?.lowercase()) {
        "kotlin", "kt" -> setOf("fun", "val", "var", "class", "data", "object", "when", "if", "else", "return", "suspend", "interface", "private", "override")
        "javascript", "js", "typescript", "ts" -> setOf("const", "let", "var", "function", "class", "return", "if", "else", "async", "await", "interface", "type")
        "python", "py" -> setOf("def", "class", "return", "if", "else", "elif", "async", "await", "import", "from", "for", "in")
        else -> emptySet()
    }
    Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(code).forEach { match ->
        if (match.value in keywords) addStyle(SpanStyle(color = Color(0xFFB277FF), fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
    }
    Regex("\"(?:\\\\.|[^\"\\\\])*\"").findAll(code).forEach { match ->
        addStyle(SpanStyle(color = Color(0xFF42B883)), match.range.first, match.range.last + 1)
    }
    Regex("//.*$|#.*$", RegexOption.MULTILINE).findAll(code).forEach { match ->
        addStyle(SpanStyle(color = Color(0xFF7D8491), fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
    }
}

private val INLINE_TOKEN = Regex("(\\*\\*[^*]+\\*\\*|__[^_]+__|~~[^~]+~~|`[^`]+`|\\[[^]]+]\\([^)]+\\)|(?<!\\*)\\*[^*]+\\*)")