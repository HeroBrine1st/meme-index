package ru.herobrine1st.memeindex.shared.model.llama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A chat request message. [content] is always a list of parts (llama.cpp accepts this form for
 * both text-only and multimodal messages).
 */
@Serializable
public data class Message(
    @SerialName("role") public val role: Role,
    @SerialName("content") public val content: List<ContentPart>,
) {
    public companion object {
        public fun user(vararg parts: ContentPart): Message = Message(Role.USER, parts.toList())
        public fun user(text: String): Message = user(ContentPart.Text(text))
    }
}