package ru.herobrine1st.memeindex.decompose.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnPause
import com.arkivanov.essenty.lifecycle.doOnResume
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import ru.herobrine1st.memeindex.shared.client.TypedAPIClient
import ru.herobrine1st.memeindex.shared.client.urlFor
import ru.herobrine1st.memeindex.shared.model.server.endpoint.MemeIndexScope
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Version
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationComponent(
    private val api: TypedAPIClient<MemeIndexScope>,
    componentContext: ComponentContext,
    private val onServerConfigured: (Url) -> Unit,
) : ComponentContext by componentContext {
    val scope = coroutineScope()

    var url: String by mutableStateOf("")
    var state: ServerCheckState by mutableStateOf(ServerCheckState(url = "", isChecking = false, isValid = false))
    val canSet get() = state.isValid && !state.isChecking && state.url == url.trim()

    /** Auto-check of the entered base path: debounced, previous check cancelled on new input. */
    private val checkFlow = snapshotFlow { url.trim() }
        .transformLatest { text ->
            delay(250.milliseconds)
            when {
                text.isBlank() -> emit(ServerCheckEvent.Invalid(text))
                else -> {
                    emit(ServerCheckEvent.Checking(text))
                    // The common /memeindex prefix doubles as the identity check:
                    // any other server 404s (or answers garbage), which fails the
                    // request.
                    api.request(Version, Unit) { urlFor(text) }
                        .onSuccess { emit(ServerCheckEvent.Valid(text)) }
                        .onFailure { emit(ServerCheckEvent.Invalid(text)) }
                }
            }
        }
        .runningFold(state) { _, event ->
            when (event) {
                is ServerCheckEvent.Checking ->
                    ServerCheckState(url = event.url, isChecking = true, isValid = false)
                is ServerCheckEvent.Valid ->
                    ServerCheckState(url = event.url, isChecking = false, isValid = true)
                is ServerCheckEvent.Invalid ->
                    ServerCheckState(url = event.url, isChecking = false, isValid = false)
            }
        }
        .onEach { state = it }

    init {
        // Check only while the screen is actually shown.
        var job: Job? = null
        lifecycle.doOnResume { job = checkFlow.launchIn(scope) }
        lifecycle.doOnPause { job?.cancel() }
    }

    /** Saves the entered base path and leaves the screen. Only valid when [canSet] is true. */
    fun setServer() {
        onServerConfigured(Url(url.trim()))
    }

    data class ServerCheckState(
        val url: String,
        val isChecking: Boolean,
        val isValid: Boolean,
    )

    sealed interface ServerCheckEvent {
        val url: String

        data class Checking(override val url: String) : ServerCheckEvent
        data class Valid(override val url: String) : ServerCheckEvent
        data class Invalid(override val url: String) : ServerCheckEvent
    }
}
