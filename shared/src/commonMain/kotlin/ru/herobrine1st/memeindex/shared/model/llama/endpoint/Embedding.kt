package ru.herobrine1st.memeindex.shared.model.llama.endpoint

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.memeindex.shared.CalledWith
import ru.herobrine1st.memeindex.shared.Method
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.shared.model.llama.ContentPart

@Resource("/embeddings")
@CalledWith(Method.POST)
public object Embedding : LlamaCppEndpoint<Embedding.Request, Embedding.Response> {

    @Serializable
    public data class Request(
        @SerialName("model") public val model: String,
        @SerialName("input") public val input: List<Content>,
    ) {
        /**
         * One element of [Request.input]: an OAI content object, `{"content": [... parts ...]}` — the
         * same part shape as `/v1/chat/completions` message content. llama.cpp accepts these for
         * multimodal embeddings; each object yields one embedding.
         */
        @Serializable
        public data class Content(
            @SerialName("content") public val content: List<ContentPart>,
        )
    }

    @Serializable
    public data class Response(
        @SerialName("data") public val data: List<EmbeddingData> = emptyList(),
        @SerialName("model") public val model: String? = null,
    ) {
        @Serializable
        public data class EmbeddingData(
            public val index: Int,
            @SerialName("embedding") public val embedding: Vector,
        )
    }
}