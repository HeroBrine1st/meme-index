package ru.herobrine1st.memeindex

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard

actual fun Clipboard.setText(text: String, label: String): Boolean {
    nativeClipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}