package ru.herobrine1st.memeindex.shared.model.server.endpoint

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import ru.herobrine1st.memeindex.shared.CalledWith
import ru.herobrine1st.memeindex.shared.Method

/**
 * Identity probe under the common `/memeindex` prefix: any other server does
 * not serve that path, so a successful call is the app's proof that it talks
 * to a meme-index server. [version] is a constant for now.
 */
@Resource("/memeindex/version")
@CalledWith(Method.GET)
@Serializable
public data object Version : MemeIndexEndpoint<Unit, Version.Response> {

    @Serializable
    public data class Response(
        public val version: String,
    )
}
