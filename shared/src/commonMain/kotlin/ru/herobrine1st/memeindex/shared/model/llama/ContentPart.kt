package ru.herobrine1st.memeindex.shared.model.llama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface ContentPart {
    @SerialName("text")
    @Serializable
    public data class Text(val text: String): ContentPart

    @SerialName("image_url")
    @Serializable
    public data class Image(@SerialName("image_url") val imageUrl: OpenAIUrl): ContentPart

    public companion object {
        public fun text(text: String): Text = Text(text)
        public fun image(url: String): Image = Image(OpenAIUrl(url))
    }
}