package ru.herobrine1st.memeindex.shared.model.llama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class OpenAIUrl(@SerialName("url") val url: String)