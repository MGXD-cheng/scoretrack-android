package com.lingxi.scoretrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 极简 Markdown 渲染器 —— 只支持报告实际用得到的那几个语法。
 *
 * 为什么自己写而不引库：
 * 报告的格式是**我们自己用 prompt 约束死的**（## 标题、- 列表、**加粗**），
 * 需求边界完全已知。为此引一个几百 KB 的通用 Markdown 库不划算，
 * 而且通用库遇到不支持语法时会「静默丢内容」，自绘反而更可控。
 *
 * 支持：# / ## / ###、- 或 * 列表、**加粗**、空行分段。其余按普通段落渲染。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(markdown) { markdown.lines() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(8.dp))

                line.startsWith("### ") -> Heading(
                    text = line.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall
                )

                line.startsWith("## ") -> Heading(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium
                )

                line.startsWith("# ") -> Heading(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.titleLarge
                )

                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = line.length - line.trimStart().length
                    Bullet(
                        text = line.trimStart().removePrefix("- ").removePrefix("* "),
                        indentLevel = indent / 2
                    )
                }

                else -> Text(
                    text = parseInline(line),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = parseInline(text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun Bullet(text: String, indentLevel: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (8 + indentLevel * 12).dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = parseInline(text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 解析行内 **加粗**。
 * 用「遇到 `**` 就找下一个 `**`」的配对方式；
 * 找不到配对的就原样输出，绝不吞字符 —— 模型少写一个星号是常事。
 */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", startIndex = index + 2)
            if (end > index) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(index + 2, end))
                }
                index = end + 2
                continue
            }
        }
        append(text[index])
        index++
    }
}