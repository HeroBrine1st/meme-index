package ru.herobrine1st.memeindex.shared

import io.ktor.http.HttpMethod
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * An API endpoint declaration.
 *
 * @param Scope the scope (group) this endpoint belongs to, e.g. [ru.herobrine1st.memeindex.shared.model.server.endpoint.MemeIndexScope].
 * @param Request the HTTP request body type of the endpoint ([Unit] for body-less endpoints).
 * @param Response the HTTP response body type of the endpoint.
 */
public interface APIEndpoint<Scope : APIEndpoint<Scope, *, *>, Request : Any, Response : Any>

/**
 * Declares the HTTP method an endpoint is called with.
 *
 * The annotation is read from the endpoint's serializer descriptor at runtime by both the
 * typed client and the server's route binding, so a single endpoint declaration drives both sides.
 */
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
public annotation class CalledWith(val method: Method)

public enum class Method(public val httpMethod: HttpMethod) {
    GET(HttpMethod.Get),
    POST(HttpMethod.Post),
    PUT(HttpMethod.Put),
    DELETE(HttpMethod.Delete),
}
