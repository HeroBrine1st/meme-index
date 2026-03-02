package ru.herobrine1st.memeindex.server.module

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.defaultForFilePath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.resources.Resources
import io.ktor.server.resources.handle
import io.ktor.server.resources.resource
import io.ktor.server.response.respond
import io.ktor.server.response.respondSource
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import ru.herobrine1st.memeindex.server.Indexer
import ru.herobrine1st.memeindex.server.SUPPORTED_FILES
import ru.herobrine1st.memeindex.server.TEXT_FILES
import ru.herobrine1st.memeindex.server.generated.sqlx4k.SearchRepositoryImpl
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.shared.APIEndpoint
import ru.herobrine1st.memeindex.shared.CalledWith
import ru.herobrine1st.memeindex.shared.client.APIException
import ru.herobrine1st.memeindex.shared.client.ErrorResponse
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Version

/**
 * Binds a shared endpoint declaration as a route, mirroring the gateway's `Route.endpoint`:
 * the path template comes from `@Resource`, the HTTP method from `@CalledWith`, the request
 * body is received as the endpoint's Request type.
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified ENDPOINT : APIEndpoint<*, REQUEST, *>, reified REQUEST : Any, reified RESPONSE : Any> Route.endpoint(
    crossinline block: suspend ApplicationCall.(ENDPOINT, REQUEST) -> RESPONSE,
) {
    val annotations = serializer<ENDPOINT>().descriptor.annotations
    val httpMethod = annotations.filterIsInstance<CalledWith>()
        .firstOrNull()?.method?.httpMethod
        ?: throw IllegalArgumentException("Endpoint ${ENDPOINT::class.qualifiedName} is missing @CalledWith")
    resource<ENDPOINT> {
        method(httpMethod) {
            handle<ENDPOINT> { endpoint ->
                val body = if (REQUEST::class == Unit::class) Unit else call.receive<REQUEST>()
                try {
                    call.respond(block(call, endpoint, body as REQUEST))
                } catch (e: APIException) {
                    call.respondError(e.status, e.message)
                }
            }
        }
    }
}

suspend fun ApplicationCall.respondError(status: Int, message: String) {
    response.status(HttpStatusCode(status, "Error"))
    respond(ErrorResponse(code = "error", message = message))
}

fun ApplicationCall.respond404() {
    response.status(HttpStatusCode.NotFound)
}

/**
 * Server version reported by `/memeindex/version`.
 *
 * TODO: take this from VCS (git describe / commit hash) at build time instead
 *       of a hardcoded constant.
 */
private const val VERSION: String = "0.1.0"

fun Application.webModule() {
    val settings = components.settings

    install(ContentNegotiation) {
        json()
    }
    install(Resources)
    // FIXME host frontend on same server
    install(CORS) {
        allowOrigins { true }
        allowHeaders { true }
        anyHost()
        anyMethod()

        allowCredentials = true
        exposeHeader(HttpHeaders.ContentDisposition)
        allowNonSimpleContentTypes = true

    }

    routing {
        endpoint<Search, Search.Request, Search.Response> { _, request ->
            val queryVector = try {
                components.embedder.embedQuery(request.query)
            } catch (e: Throwable) {
                throw APIException("Failed to embed the query", 502, e)
            }
            val hits = SearchRepositoryImpl.findAllBySearchCriteria(
                components.db,
                queryVector = queryVector,
                q = request.query,
                vectorImage = request.vectorImage,
                vectorText = request.vectorText,
                fullText = request.fullText,
                model = components.embedder.modelName,
            ).getOrThrow()

            // The database stores paths relative to the media root; the result URL is
            // the configured public base URL with `/media/<segments>` appended
            // (`appendPathSegments` percent-encodes each segment).
            val results = hits.map { hit ->
                Search.Response.SearchResult(
                    url = URLBuilder().takeFrom(settings.publicBaseUrl)
                        .appendPathSegments("memeindex")
                        .appendPathSegments("media")
                        .appendPathSegments(hit.path, encodeSlash = false) // windows is not supported
                        .build(),
                    text = hit.text,
                    type = if (Indexer.extension(Path(hit.path)) in TEXT_FILES) {
                        Search.Response.ResultType.Text
                    } else {
                        Search.Response.ResultType.Image
                    },
                    textSimilarity = hit.textSimilarity?.toFloat(),
                    textVectorSimilarity = hit.textVectorSimilarity?.toFloat(),
                    imageVectorSimilarity = hit.imageVectorSimilarity?.toFloat(),
                )
            }
            val total = hits.firstOrNull()?.totalCount ?: 0
            Search.Response(results, total)
        }

        endpoint<Version, Unit, Version.Response> { _, _ ->
            Version.Response(version = VERSION)
        }

        // Port of the Python FilteredStaticFiles: only supported extensions, no traversal.
        route("/memeindex/media") {
            get("/{path...}") {
                // The `...` tailcard stores every path segment as a separate
                // value under the same parameter name.
                val segments = call.parameters.getAll("path") ?: emptyList()
                if (segments.isEmpty() || segments.all { it.isBlank() }) {
                    call.respond404()
                    return@get
                }
                // FIXME SECURITY: resolve path ABSOLUTE to "/", then that resulting path relative to media root
                if (segments.any { it == "." || it == ".." }) {
                    call.respond404()
                    return@get
                }
                val file = Path(settings.mediaPath, *segments.filter { it.isNotEmpty() }.toTypedArray())
                val meta = withContext(Dispatchers.IO) { SystemFileSystem.metadataOrNull(file) }
                if (meta?.isRegularFile != true || Indexer.extension(file) !in SUPPORTED_FILES) {
                    call.respond404()
                    return@get
                }
                call.respondSource(
                    source = SystemFileSystem.source(file),
                    contentType = ContentType.defaultForFilePath(file.name),
                    contentLength = meta.size,
                )
            }
        }
    }
}
