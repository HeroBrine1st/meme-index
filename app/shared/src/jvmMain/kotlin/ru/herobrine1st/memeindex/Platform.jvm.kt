package ru.herobrine1st.memeindex

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun Clipboard.setText(text: String, label: String): Boolean {
    val selection = StringSelection(text)
    return awtClipboard?.setContents(selection, selection) != null
}