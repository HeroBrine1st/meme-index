package ru.herobrine1st.memeindex.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
@ReadOnlyComposable
expect fun getApplicationColorScheme(useDarkTheme: Boolean = isSystemInDarkTheme()): ColorScheme