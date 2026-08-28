package com.nitronbox.app.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `parser separates rich blocks`() {
        val source = """# Header

Paragraph with **bold**.

> Quote

| A | B |
| --- | --- |
| 1 | 2 |

```kotlin
val answer = 42
```

$$
x^2
$$"""

        val blocks = MarkdownParser.parse(source)

        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks.any { it is MarkdownBlock.Table })
        assertTrue(blocks.any { it is MarkdownBlock.Code })
        assertTrue(blocks.any { it is MarkdownBlock.Math })
        assertEquals(blocks.size, blocks.map(MarkdownBlock::stableId).distinct().size)
    }
}