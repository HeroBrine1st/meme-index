package ru.herobrine1st.memeindex

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
actual fun Clipboard.setText(text: String, label: String): Boolean {
    nativeClipboard.writeText(text) // returns Promise<Nothing>???
    return true
}