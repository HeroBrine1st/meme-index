package ru.herobrine1st.memeindex.shared.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.request
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import ru.herobrine1st.memeindex.shared.APIEndpoint
import ru.herobrine1st.memeindex.shared.CalledWith

/**
 * A failure returned by the API (non-2xx status or transport error).
 */
public class APIException(
    override val message: String,
    public val status: Int = 500,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
public data class ErrorResponse(
    public val code: String? = null,
    public val message: String? = null,
)

/**
 * A typed API client for one endpoint [SCOPE].
 *
 * The client must have the [Resources] and a JSON [io.ktor.client.plugins.contentnegotiation.ContentNegotiation]
 * plugin installed (see [TypedAPIClient.of]); endpoint paths and query parameters are resolved from
 * the `@Resource` annotation on the endpoint type, and the HTTP method from `@CalledWith`.
 */
public class TypedAPIClient<SCOPE : APIEndpoint<SCOPE, *, *>> public constructor(public val client: HttpClient) {

    @OptIn(ExperimentalSerializationApi::class)
    public suspend inline fun <reified ENDPOINT, reified REQUEST : Any, reified RESPONSE : Any> request(
        endpoint: ENDPOINT,
        body: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
    ): Result<RESPONSE> where ENDPOINT : APIEndpoint<SCOPE, REQUEST, RESPONSE> {
        val response: HttpResponse = try {
            client.request<ENDPOINT>(endpoint) {
                method = serializer<ENDPOINT>().descriptor.annotations
                    .filterIsInstance<CalledWith>()
                    .firstOrNull()?.method?.httpMethod
                    ?: throw IllegalArgumentException("Endpoint ${ENDPOINT::class.qualifiedName} is missing @CalledWith")
                if (body != Unit) {
                    // Ktor 3's ContentNegotiation only serializes bodies whose request
                    // declares (or defaults to) a content type the plugin handles.
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(body)
                }
                requestBuilder()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return Result.failure(t)
        }

        return try {
            if (response.status.isSuccess()) Result.success(response.body<RESPONSE>())
            else Result.failure(response.toAPIException())
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    public suspend inline fun <reified ENDPOINT, reified RESPONSE : Any> request(
        endpoint: ENDPOINT,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
    ): Result<RESPONSE> where ENDPOINT : APIEndpoint<SCOPE, Unit, RESPONSE> =
        request(endpoint, Unit, requestBuilder)

    public companion object {
        /**
         * Builds a client from [config] with the [Resources] plugin installed.
         * [config] should also install ContentNegotiation (JSON) and pick an engine.
         */
        public fun <SCOPE : APIEndpoint<SCOPE, Any, Any>> of(
            config: HttpClientConfig<*>.() -> Unit,
        ): TypedAPIClient<SCOPE> = TypedAPIClient(
            HttpClient {
                install(Resources)
                config(this)
            },
        )
    }
}

/**
 * Points this request at [base] (an OpenAI-style base URL like
 * `https://host/v1`): protocol, host and port are taken from it, and its path
 * prefix is prepended to the resource path resolved from the `@Resource`
 * annotation. Must be called from a request block AFTER the path is resolved
 * (i.e. as the `requestBuilder` of [TypedAPIClient.request]).
 */
public fun HttpRequestBuilder.urlFor(base: String) {
    val parsed = Url(base)
    val prefixSegments = parsed.encodedPath.trim('/').split('/').filter { it.isNotEmpty() }
    url {
        protocol = parsed.protocol
        host = parsed.host
        port = parsed.port
        if (prefixSegments.isNotEmpty()) encodedPathSegments = prefixSegments + encodedPathSegments
    }
}

/**
 * Converts a non-2xx response into an [APIException], reading a JSON [ErrorResponse] body if present.
 */
public suspend fun HttpResponse.toAPIException(): APIException {
    val status = this.status
    val description = try {
        // Tolerates flat `{"code":...,"message":...}` and nested
        // `{"error":{"code":...,"message":...}}` / `{"error":"..."}` shapes.
        val obj = body<JsonObject>()
        val error = obj["error"]
        val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.content
            ?: obj["message"]?.jsonPrimitive?.content
            ?: (error as? JsonPrimitive)?.takeIf { it.isString }?.content
        val code = (error as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
            ?: obj["code"]?.jsonPrimitive?.contentOrNull
        val detail = message?.takeIf { it.isNotBlank() } ?: code?.takeIf { it.isNotBlank() }
        if (detail == null) status.description else "${status.description}: $detail"
    } catch (t: Throwable) {
        status.description
    }
    return APIException(description, status.value)
}
