package ru.herobrine1st.memeindex.ui

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import ru.herobrine1st.memeindex.decompose.component.RootComponent
import ru.herobrine1st.memeindex.ui.screen.SearchScreen
import ru.herobrine1st.memeindex.ui.screen.ServerConfigurationScreen

@Composable
fun Navigator(rootComponent: RootComponent) {
    Children(rootComponent.childStack) {
        when(val child = it.instance) {
            is RootComponent.Child.Home -> SearchScreen(child.component)
            is RootComponent.Child.ServerConfiguration -> ServerConfigurationScreen(child.component)
        }
    }
}