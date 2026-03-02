package ru.herobrine1st.memeindex.server.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser

object SuryaUtils {
    private val NO_TEXT_LABELS: Set<String> =
        setOf("Figure", "Picture", "Diagram", "BlankPage", "Image", "Complex-Block", "Blank-Page")

    fun extractOcrText(raw: String): String? = raw.trim()
        .let {
            if (it.startsWith("```") && it.endsWith("```")) {
                it.replaceFirst(Regex("^```[^\n]*"), "")
                    .removeSuffix("```")
            } else it
        }
        .replace(COMPACT_LABEL_REGEX, "\n")
        .replace(COMPACT_TOKEN_REGEX, "")
        .replace(PARTIAL_COMPACT_HEADER_REGEX, "")
        .let { html ->
            Parser.htmlParser().setTrackErrors(1).let { parser ->
                Ksoup.parse(html, parser)
                    .also {
                        if (parser.getErrors().isNotEmpty()) {
                            throw IllegalStateException(
                                "OCR output is not well-formed HTML: ${parser.getErrors().first()}",
                            )
                        }
                    }
            }
        }
        .apply {
            outputSettings().prettyPrint(false)
            body().children()
                .filter { it.attr("data-label") in NO_TEXT_LABELS }
                .forEach(Element::remove)
        }
        .apply<Element> {
            select("div,p,h1,h2,h3,h4,h5,h6,li,tr,table,br").forEach { el -> el.after("\n") }
        }.wholeText()
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .ifBlank { null }

    private val COMPACT_LABEL_REGEX = Regex("<\\|[A-Z][A-Za-z-]*\\|>")
    private val COMPACT_TOKEN_REGEX = Regex("<\\|[^<>|]*\\|>")
    private val PARTIAL_COMPACT_HEADER_REGEX = Regex(
        "<\\|[A-Za-z-]+\\|>(?:<\\|\\d{1,4}\\|>){0,3}$|<\\|[^<>|]*$",
    )
}
