package ru.herobrine1st.memeindex.util

import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search

interface ShareLauncher {
    suspend fun share(item: Search.Response.SearchResult): Result<Unit>
}