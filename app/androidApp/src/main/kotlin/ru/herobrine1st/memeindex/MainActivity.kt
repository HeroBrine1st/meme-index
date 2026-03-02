package ru.herobrine1st.memeindex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import ru.herobrine1st.memeindex.decompose.component.RootComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val root = RootComponent(
            shareLauncherProvider = { httpClient -> ShareLauncherImpl(this, httpClient) },
            componentContext = defaultComponentContext(),
        )

        setContent {
            App(root)
        }
    }
}