package ru.herobrine1st.memeindex.shared.model.llama.endpoint

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.memeindex.shared.CalledWith
import ru.herobrine1st.memeindex.shared.Method
import ru.herobrine1st.memeindex.shared.model.llama.Message
@Resource("/chat/completions")
@CalledWith(Method.POST)
public object ChatCompletion : LlamaCppEndpoint<ChatCompletion.Request, ChatCompletion.Response> {
    @Serializable
    public data class Request(
        @SerialName("model") public val model: String,
        @SerialName("messages") public val messages: List<Message>,
        @SerialName("temperature") public val temperature: Double,
    ) {
    }

    @Serializable
    public data class Response(
        public val id: String,
        @SerialName("object") public val objectType: String? = null,
        @SerialName("created") public val created: Long? = null,
        @SerialName("model") public val model: String? = null,
        @SerialName("choices") public val choices: List<Choice> = emptyList(),
    ) {
        public val content: String?
            get() = choices.firstOrNull()?.message?.content
    }

    @Serializable
    public data class Choice(
        public val index: Int,
        @SerialName("message") public val message: ChatMessage,
        @SerialName("finish_reason") public val finishReason: String? = null,
    ) {
        @Serializable
        public data class ChatMessage(
            @SerialName("role") public val role: String,
            @SerialName("content") public val content: String? = null,
        )
    }
}