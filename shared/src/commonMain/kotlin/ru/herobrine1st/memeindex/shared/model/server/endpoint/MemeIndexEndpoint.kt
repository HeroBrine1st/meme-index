package ru.herobrine1st.memeindex.shared.model.server.endpoint

import ru.herobrine1st.memeindex.shared.APIEndpoint

public interface MemeIndexEndpoint<Request : Any, Response : Any> : APIEndpoint<MemeIndexScope, Request, Response>

public typealias MemeIndexScope = MemeIndexEndpoint<Any, Any>