package ru.herobrine1st.memeindex

import androidx.compose.ui.platform.Clipboard

expect fun Clipboard.setText(text: String, label: String): Boolean
