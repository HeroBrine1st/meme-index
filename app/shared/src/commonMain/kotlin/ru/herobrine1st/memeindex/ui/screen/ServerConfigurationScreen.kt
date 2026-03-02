package ru.herobrine1st.memeindex.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.herobrine1st.memeindex.decompose.component.ServerConfigurationComponent
import ru.herobrine1st.memeindex.resources.Res
import ru.herobrine1st.memeindex.resources.action_set_server
import ru.herobrine1st.memeindex.resources.splash_invalid_server
import ru.herobrine1st.memeindex.resources.splash_server_base_path
import ru.herobrine1st.memeindex.resources.splash_welcome

/**
 * Shown when no server base path is configured: a welcome message in the top
 * half and, centered, the input field for the base path. The path is checked
 * automatically while it is typed (see [ServerConfigurationComponent]); the
 * trailing button and the IME action only become active once the check for
 * the current text has confirmed a meme-index server.
 */
@Composable
fun ServerConfigurationScreen(component: ServerConfigurationComponent) {
    val state = component.state
    val canSet = component.canSet

    // Paints the theme background like SearchScreen's Scaffold does; without it
    // the always-light window background shows through in dark theme.
    Scaffold(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.splash_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlinedTextField(
                value = component.url,
                onValueChange = { component.url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.splash_server_base_path)) },
                singleLine = true,
                trailingIcon = {
                    if (state.isChecking && state.url == component.url.trim()) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.4.dp)
                    } else if (canSet) {
                        IconButton(enabled = canSet, onClick = { component.setServer() }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.action_set_server))
                        }
                    }
                },
                supportingText = {
                    if (component.url.isNotBlank() && !state.isChecking && !state.isValid) {
                        Text(stringResource(Res.string.splash_invalid_server))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSet) component.setServer() }),
            )
        }
        }
    }
}
