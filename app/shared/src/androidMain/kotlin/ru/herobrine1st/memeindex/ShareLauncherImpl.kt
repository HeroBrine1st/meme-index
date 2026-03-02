package ru.herobrine1st.memeindex

import android.content.Context
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.shareFile
import io.github.vinceglb.filekit.utils.div
import io.github.vinceglb.filekit.utils.toKotlinxPath
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search
import ru.herobrine1st.memeindex.util.ShareLauncher

class ShareLauncherImpl(
    private val context: Context,
    private val httpClient: HttpClient,
) : ShareLauncher {
    override suspend fun share(item: Search.Response.SearchResult): Result<Unit> = runCatching {
        val response: HttpResponse = httpClient.get(item.url) { expectSuccess = true }

        (context.cacheDir.toKotlinxPath() / "shared")
            .also(SystemFileSystem::createDirectories)
            .div(item.url.segments.last())
            .also { path ->
                SystemFileSystem.sink(path)
                    .buffered()
                    .transferFrom(response.bodyAsChannel().asSource())
            }
            .also { path ->
                FileKit.shareFile(PlatformFile(path))
            }
            .also(SystemFileSystem::delete)
    }
}