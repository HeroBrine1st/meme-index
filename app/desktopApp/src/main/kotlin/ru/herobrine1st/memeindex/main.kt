package ru.herobrine1st.memeindex

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ru.herobrine1st.memeindex.decompose.component.RootComponent
import javax.swing.SwingUtilities

fun main() {
    val lifecycle = LifecycleRegistry()
    val rootComponent = runOnUiThread { RootComponent(componentContext = DefaultComponentContext(lifecycle)) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MemeIndex",
        ) {
            App(rootComponent)
        }
    }
}

fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }

    var error: Throwable? = null
    var result: T? = null

    SwingUtilities.invokeAndWait {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        }
    }

    error?.also { throw it }

    @Suppress("UNCHECKED_CAST")
    return result as T
}