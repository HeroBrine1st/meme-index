package ru.herobrine1st.memeindex.decompose

import kotlinx.serialization.Serializable

@Serializable
sealed interface Config {
    @Serializable
    data object Home : Config

    @Serializable
    data object ServerConfiguration : Config
}
