package ru.herobrine1st.memeindex.decompose.component

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import coil3.request.crossfade
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.herobrine1st.memeindex.decompose.Config
import ru.herobrine1st.memeindex.preferences.Preferences
import ru.herobrine1st.memeindex.preferences.createPreferencesStorage
import ru.herobrine1st.memeindex.preferences.readPreferencesBlocking
import ru.herobrine1st.memeindex.shared.client.TypedAPIClient
import ru.herobrine1st.memeindex.shared.model.server.endpoint.MemeIndexScope
import ru.herobrine1st.memeindex.util.ShareLauncher
import ru.herobrine1st.memeindex.util.immediateOrFallback

@OptIn(ExperimentalCoilApi::class)
class RootComponent(
    httpClientConfiguration: HttpClientConfig<*>.() -> Unit = {},
    shareLauncherProvider: (HttpClient) -> ShareLauncher? = { null },
    componentContext: ComponentContext,
) : ComponentContext by componentContext {
    private val navigation = StackNavigation<Config>()

    private val scope = coroutineScope(Dispatchers.Main.immediateOrFallback + SupervisorJob())
    private val dataStore: DataStore<Preferences> = DataStoreFactory.create(
        createPreferencesStorage(),
        scope = scope,
    )

    private val serverBasePath = MutableStateFlow(readPreferencesBlocking(dataStore).serverBasePath)

    val httpClient = HttpClient {
        defaultRequest {
            serverBasePath.value?.let { url(it.toString()) }
        }
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 120_000
        }
        httpClientConfiguration()
    }
    private val api: TypedAPIClient<MemeIndexScope> = TypedAPIClient(httpClient)
    private val shareLauncher = shareLauncherProvider(httpClient)

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = if (serverBasePath.value == null) Config.ServerConfiguration else Config.Home,
        handleBackButton = true,
        childFactory = { cfg, ctx ->
            when (cfg) {
                Config.Home -> Child.Home(SearchComponent(api, shareLauncher, ctx))
                Config.ServerConfiguration -> Child.ServerConfiguration(
                    ServerConfigurationComponent(
                        api, componentContext = ctx,
                        onServerConfigured = {
                            serverBasePath.value = it
                            scope.launch {
                                dataStore.updateData { preferences -> preferences.copy(serverBasePath = it) }
                            }
                            navigation.replaceCurrent(Config.Home)
                        },
                    ),
                )
            }
        },
    )

    init {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .components {
                    add(NetworkFetcher.Factory(networkClient = { httpClient.asNetworkClient() }))
                }
                .build()
        }
    }

    sealed interface Child {
        class Home(val component: SearchComponent) : Child
        class ServerConfiguration(val component: ServerConfigurationComponent) : Child
    }
}