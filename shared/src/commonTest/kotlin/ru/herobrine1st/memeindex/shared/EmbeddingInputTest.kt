package ru.herobrine1st.memeindex.shared

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.herobrine1st.memeindex.shared.model.llama.ContentPart
import ru.herobrine1st.memeindex.shared.model.llama.endpoint.Embedding
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddingInputTest {
    private val json = Json

    @Test
    fun textSerializesToTypedPart() {
        val encoded = json.encodeToString(ContentPart.serializer(), ContentPart.text("hello"))
        assertEquals("""{"type":"text","text":"hello"}""", encoded)
        assertEquals(ContentPart.text("hello"), json.decodeFromString(ContentPart.serializer(), encoded))
    }

    @Test
    fun imageSerializesToTypedPart() {
        val url = "data:image/png;base64,AAA="
        val encoded = json.encodeToString(ContentPart.serializer(), ContentPart.image(url))
        val part = json.parseToJsonElement(encoded).jsonObject
        assertEquals("image_url", part["type"]!!.jsonPrimitive.content)
        assertEquals(url, part["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals(ContentPart.image(url), json.decodeFromString(ContentPart.serializer(), encoded))
    }

    @Test
    fun mixedInputListSerializesToContentObjects() {
        val input = listOf(
            Embedding.Request.Content(listOf(ContentPart.text("a"))),
            Embedding.Request.Content(listOf(ContentPart.image("u2"))),
            Embedding.Request.Content(listOf(ContentPart.text("c"))),
        )
        val serializer = ListSerializer(Embedding.Request.Content.serializer())
        val encoded = json.encodeToString(serializer, input)
        assertEquals(
            """[{"content":[{"type":"text","text":"a"}]},{"content":[{"type":"image_url","image_url":{"url":"u2"}}]},{"content":[{"type":"text","text":"c"}]}]""",
            encoded,
        )
        assertEquals(input, json.decodeFromString(serializer, encoded))
    }
}
