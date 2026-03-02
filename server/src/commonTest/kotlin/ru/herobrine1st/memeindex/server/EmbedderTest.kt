package ru.herobrine1st.memeindex.server

import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EmbedderTest {
    @Test
    fun stripHtmlConvertsBlockTagsAndBrToNewlines() {
        assertEquals("Hello\nWorld", Embedder.stripHtml("<div>Hello</div><div>World</div>"))
        assertEquals("Title\na\nb", Embedder.stripHtml("<h1>Title</h1><ul><li>a</li><li>b</li></ul>"))
        assertEquals("one\ntwo", Embedder.stripHtml("<p>one<br>two</p>"))
        assertEquals("a b", Embedder.stripHtml("<div>a <span>b</span></div>"))
        assertEquals("x\ny", Embedder.stripHtml("x<br/>y"))
    }

    @Test
    fun stripHtmlPassesPlainTextThrough() {
        assertEquals("plain text line", Embedder.stripHtml("plain text line"))
        assertEquals("a\nb", Embedder.stripHtml("a\nb"))
    }

    @Test
    fun stripHtmlTrimsEmptyLines() {
        assertEquals("a\nb", Embedder.stripHtml("<div>a</div><div></div><div>b</div>"))
    }

    @Test
    fun stripHtmlDropsMarkdownCodeFences() {
        assertEquals("hello", Embedder.stripHtml("```html\nhello\n```"))
    }

    @Test
    fun dataUriPrefixesBase64Png() {
        assertEquals("data:image/png;base64,aGVsbG8=", Embedder.dataUri(ByteString("hello".encodeToByteArray())))
    }

    @Test
    fun extractOcrTextJoinsTextBlocksInOrder() {
        assertEquals(
            "Hello\nWorld",
            Embedder.extractOcrText(
                "<div data-label=\"Text\" data-bbox=\"0 0 500 100\">Hello</div>" +
                    "<div data-label=\"Section-Header\" data-bbox=\"0 100 500 150\">World</div>",
            ),
        )
    }

    @Test
    fun extractOcrTextSkipsNonTextBlocks() {
        assertEquals(
            "keep",
            Embedder.extractOcrText(
                "<div data-label=\"Figure\" data-bbox=\"0 0 10 10\">fig</div>" +
                    "<div data-label=\"Image\" data-bbox=\"0 0 10 10\">img</div>" +
                    "<div data-label=\"Diagram\" data-bbox=\"0 0 10 10\">dia</div>" +
                    "<div data-label=\"Blank-Page\" data-bbox=\"0 0 10 10\">blank</div>" +
                    "<div data-label=\"Complex-Block\" data-bbox=\"0 0 10 10\">complex</div>" +
                    "<div data-label=\"Text\" data-bbox=\"0 0 10 10\">keep</div>",
            ),
        )
    }

    @Test
    fun extractOcrTextSkipsDivsWithoutLabelOrBbox() {
        assertEquals(
            "ok",
            Embedder.extractOcrText(
                "<div>Hello</div>" +
                    "<div data-label=\"Text\">no bbox</div>" +
                    "<div data-bbox=\"0 0 1 1\">no label</div>" +
                    "<div data-label=\"Text\" data-bbox=\"not numbers\">bad bbox</div>" +
                    "<div data-label=\"Text\" data-bbox=\"0 0 1 1\">ok</div>",
            ),
        )
    }

    @Test
    fun extractOcrTextKeepsNestedDivsInsideParent() {
        assertEquals(
            "ab\nc",
            Embedder.extractOcrText("<div data-label=\"Text\" data-bbox=\"0 0 1 1\">a<div>b</div>c</div>"),
        )
    }

    @Test
    fun extractOcrTextStripsFencesBeforeParsing() {
        assertEquals(
            "hi",
            Embedder.extractOcrText("```html\n<div data-label=\"Text\" data-bbox=\"0 0 1 1\">hi</div>\n```"),
        )
    }

    @Test
    fun extractOcrTextThrowsOnJson() {
        assertFailsWith<IllegalStateException> { Embedder.extractOcrText("[{\"label\": \"Text\"}]") }
        assertFailsWith<IllegalStateException> { Embedder.extractOcrText("{\"blocks\": []}") }
    }

    @Test
    fun extractOcrTextReturnsNullForBlank() {
        assertNull(Embedder.extractOcrText(""))
        assertNull(Embedder.extractOcrText("   \n  "))
        assertNull(Embedder.extractOcrText("```\n```"))
    }

    @Test
    fun extractOcrTextReturnsNullWhenAllBlocksSkipped() {
        assertNull(Embedder.extractOcrText("<div data-label=\"Figure\" data-bbox=\"0 0 1 1\">x</div>"))
    }

    @Test
    fun extractOcrTextPassesPlainTextThrough() {
        assertEquals("plain text", Embedder.extractOcrText("plain text"))
    }
}
