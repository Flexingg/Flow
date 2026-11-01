package com.flow.todo.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.flow.todo.ui.theme.FluidTheme

private val INLINE_MARKDOWN_REGEX = Regex(
    """(`[^`]+`|\*\*[^*]+\*\*|~~[^~]+~~|\*[^*\s][^*]*\*)"""
)

@Composable
fun FluidMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fontSize: TextUnit = 13.sp,
    color: Color = FluidTheme.colors.textSecondary
) {
    val primaryColor = FluidTheme.colors.textPrimary
    val accentColor = FluidTheme.colors.accent
    val successColor = FluidTheme.colors.success

    val annotatedString = remember(markdown, primaryColor, accentColor, successColor, color) {
        parseFluidMarkdown(
            raw = markdown,
            primaryColor = primaryColor,
            accentColor = accentColor,
            successColor = successColor
        )
    }

    Text(
        text = annotatedString,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = (fontSize.value * 1.38f).sp,
        modifier = modifier
    )
}

private fun parseFluidMarkdown(
    raw: String,
    primaryColor: Color,
    accentColor: Color,
    successColor: Color
): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        when {
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("### "), primaryColor, accentColor)
                }
            }
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("## "), primaryColor, accentColor)
                }
            }
            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    appendInlineMarkdown(trimmed.removePrefix("# "), primaryColor, accentColor)
                }
            }
            trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true) -> {
                withStyle(SpanStyle(color = successColor, fontWeight = FontWeight.Bold)) {
                    append("✓ ")
                }
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlineMarkdown(trimmed.substring(6), primaryColor, accentColor)
                }
            }
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                    append("○ ")
                }
                appendInlineMarkdown(trimmed.substring(6), primaryColor, accentColor)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                    append("• ")
                }
                appendInlineMarkdown(trimmed.substring(2), primaryColor, accentColor)
            }
            else -> {
                appendInlineMarkdown(rawLine, primaryColor, accentColor)
            }
        }
        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    primaryColor: Color,
    accentColor: Color
) {
    var cursor = 0
    for (match in INLINE_MARKDOWN_REGEX.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = accentColor.copy(alpha = 0.14f),
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            }
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
