package ru.herobrine1st.memeindex.shared.model.server.endpoint

import io.ktor.http.Url
import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.memeindex.shared.CalledWith
import ru.herobrine1st.memeindex.shared.Method

@Resource("/memeindex/search")
@CalledWith(Method.POST)
@Serializable
public data object Search : MemeIndexEndpoint<Search.Request, Search.Response> {

    @Serializable
    public data class Request(
        public val query: String,
        @SerialName("full_text") public val fullText: Boolean = true,
        @SerialName("vector_text") public val vectorText: Boolean = true,
        @SerialName("vector_image") public val vectorImage: Boolean = true,
    )

    @Serializable
    public data class Response(
        public val results: List<SearchResult>,
        public val total: Int,
    ) {
        /** Whether the hit is an image or a text document (derived from the extension). */
        @Serializable
        public enum class ResultType {
            @SerialName("image") Image,
            @SerialName("text") Text,
        }

        @Serializable
        public data class SearchResult(
            public val url: Url,
            public val type: ResultType,
            public val text: String? = null,
            public val textSimilarity: Float? = null,
            public val textVectorSimilarity: Float? = null,
            public val imageVectorSimilarity: Float? = null,
        )
    }
}