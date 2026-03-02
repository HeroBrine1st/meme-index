package ru.herobrine1st.memeindex.server.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.coroutineScope
import kotlinx.io.bytestring.ByteString
import ru.herobrine1st.memeindex.server.Settings
import ru.herobrine1st.memeindex.shared.client.TypedAPIClient
import ru.herobrine1st.memeindex.shared.client.urlFor
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.shared.model.llama.ContentPart
import ru.herobrine1st.memeindex.shared.model.llama.Message
import ru.herobrine1st.memeindex.shared.model.llama.endpoint.ChatCompletion
import ru.herobrine1st.memeindex.shared.model.llama.endpoint.Embedding
import ru.herobrine1st.memeindex.shared.model.llama.endpoint.LlamaCppScope
import kotlin.io.encoding.Base64

class Embedder(
    private val llama: TypedAPIClient<LlamaCppScope>,
    private val settings: Settings,
) {
    private val logger = KotlinLogging.logger {}

    val modelName: String
        get() = settings.embeddings.model

    suspend fun embedText(texts: List<String>): List<Vector> {
        if (texts.isEmpty()) return emptyList()
        return runCatching {
            embedContent(
                texts.map { text ->
                    Embedding.Request.Content(
                        listOf(ContentPart.text(settings.embeddings.documentFormat.replaceFirst("%s", text))),
                    )
                },
            )
        }
            .onFailure { logger.warn(it) { "Failed to embed text" } }
            .getOrThrow()
    }

    suspend fun embedImages(pngs: List<ByteString>): List<Vector> {
        if (pngs.isEmpty()) return emptyList()

        return runCatching {
            embedContent(
                pngs.map { png ->
                    Embedding.Request.Content(
                        listOf(ContentPart.image(dataUri(png))),
                    )
                },
            )
        }.recoverCatching {
            logger.warn(it) { "Failed to batch embed images, falling back to single requests" }
            coroutineScope {
                pngs.map { png ->
                    embedContent(
                        listOf(
                            Embedding.Request.Content(
                                listOf(ContentPart.image(dataUri(png))),
                            ),
                        ),
                    ).single()
                }
            }
        }.onFailure { logger.error(it) { "Failed to embed image" } }.getOrThrow()
    }

    suspend fun embedQuery(text: String) = embedText(
        listOf(
            settings.embeddings.queryFormat.replaceFirst("%s", text),
        ),
    ).first()

    suspend fun ocr(png: ByteString, temperature: Double): String {
        val response = llama.request(
            ChatCompletion,
            ChatCompletion.Request(
                model = settings.ocr.model,
                temperature = temperature,
                messages = listOf(
                    Message.user(
                        ContentPart.text(settings.ocr.prompt),
                        ContentPart.image(dataUri(png)),
                    ),
                ),
            ),
            requestBuilder = {
                urlFor(settings.ocr.baseUrl)
                settings.ocr.apiKey?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            },
        ).getOrThrow()
        val content = response.content ?: error("OCR response has no choices/content")
        if (response.choices.firstOrNull()?.finishReason != "stop") {
            throw IllegalStateException(
                "OCR output was not finished cleanly (finish_reason=${response.choices.firstOrNull()?.finishReason})",
            )
        }
        return content
    }

    private suspend fun embedContent(inputs: List<Embedding.Request.Content>): List<Vector> {
        val response = llama.request(
            Embedding,
            Embedding.Request(model = modelName, input = inputs),
            requestBuilder = {
                urlFor(settings.embeddings.baseUrl)
                settings.embeddings.apiKey?.takeIf { it.isNotBlank() }
                    ?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            },
        ).getOrThrow()
        return response.data.sortedBy { it.index }.map { it.embedding }
    }

    companion object {
        fun dataUri(png: ByteString): String =
            "data:image/png;base64," + Base64.encode(png.toByteArray())

    }
}
