package ru.herobrine1st.memeindex

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.browser.window
import ru.herobrine1st.memeindex.decompose.component.RootComponent

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    val lifecycle = LifecycleRegistry()
    lifecycle.resume()
    val backDispatcher = BackDispatcher()

    window.onpopstate = {
        window.history.pushState("dummy string".toJsString(), "Dummy")
        backDispatcher.back()
    }

    val root = RootComponent(componentContext = DefaultComponentContext(lifecycle, backHandler = backDispatcher))
    ComposeViewport {
        App(root)
    }
}