package ru.herobrine1st.memeindex

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import ru.herobrine1st.memeindex.decompose.component.RootComponent
import ru.herobrine1st.memeindex.ui.Navigator
import ru.herobrine1st.memeindex.ui.getApplicationColorScheme

@Composable
fun App(rootComponent: RootComponent) {
    MaterialTheme(colorScheme = getApplicationColorScheme()) {
        Navigator(rootComponent)
    }
}