package ru.herobrine1st.memeindex.decompose.component

import androidx.compose.runtime.snapshotFlow
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import ru.herobrine1st.memeindex.shared.client.TypedAPIClient
import ru.herobrine1st.memeindex.shared.model.server.endpoint.MemeIndexScope
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search
import ru.herobrine1st.memeindex.util.ShareLauncher
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SearchComponent(
    private val api: TypedAPIClient<MemeIndexScope>,
    private val shareLauncher: ShareLauncher?,
    componentContext: ComponentContext,
) : ComponentContext by componentContext {
    private val scope = coroutineScope()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getResultsFlow(request: () -> Search.Request) = snapshotFlow { request() }
        .drop(1)
        .mapLatest {
            delay(250.milliseconds)
            it
        }
        .transformLatest { request ->
            var delay = 1.seconds
            if (request.query.isBlank()) emit(SearchRequestEvent.Success(request, emptyList()))
            else while (true) {
                emit(SearchRequestEvent.Loading(query = request))
                api.request(Search, request)
                    .onSuccess {
                        emit(SearchRequestEvent.Success(request, it.results))
                        break
                    }
                    .onFailure {
                        it.printStackTrace()
                        emit(SearchRequestEvent.Error(request, retrying = false))
                        delay(delay)
                        // Exponential backoff, capped so a persistent outage keeps
                        // polling instead of stalling for minutes between attempts.
                        delay = (delay * 1.5).coerceAtMost(30.seconds)
                    }

            }
        }
        .runningFold(
            SearchRequestState(
                isLoading = false,
                isLastError = false,
                query = request(),
                lastSuccess = null,
            ),
        ) { state, event ->
            when (event) {
                is SearchRequestEvent.Error -> state.copy(isLoading = false, isLastError = true, query = event.query)
                is SearchRequestEvent.Loading -> state.copy(isLoading = true, query = event.query)
                is SearchRequestEvent.Success -> SearchRequestState(
                    isLoading = false,
                    isLastError = false,
                    query = event.query,
                    lastSuccess = event,
                )
            }
        }

    fun shareItem(item: Search.Response.SearchResult, onFailure: () -> Unit) {
        scope.launch {
            shareLauncher?.share(item)?.onFailure { onFailure() }
        }
    }

    val isSharingSupported get() = shareLauncher != null

    data class SearchRequestState(
        val isLoading: Boolean,
        val isLastError: Boolean,
        val query: Search.Request,
        val lastSuccess: SearchRequestEvent.Success?,
    )

    sealed interface SearchRequestEvent {
        val query: Search.Request

        data class Loading(
            override val query: Search.Request,
        ) : SearchRequestEvent

        data class Success(
            override val query: Search.Request,
            val result: List<Search.Response.SearchResult>,
        ) : SearchRequestEvent

        data class Error(override val query: Search.Request, val retrying: Boolean) :
            SearchRequestEvent
    }
}
