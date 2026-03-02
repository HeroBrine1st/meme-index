package ru.herobrine1st.memeindex.shared.model.llama.endpoint

import ru.herobrine1st.memeindex.shared.APIEndpoint

/**
 * Scope of llama.cpp's OpenAI-compatible API endpoints.
 */
public interface LlamaCppEndpoint<Request : Any, Response : Any> : APIEndpoint<LlamaCppScope, Request, Response>

public typealias LlamaCppScope = LlamaCppEndpoint<Any, Any>

