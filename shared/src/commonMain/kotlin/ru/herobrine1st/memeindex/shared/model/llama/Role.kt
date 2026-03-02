package ru.herobrine1st.memeindex.shared.model.llama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class Role {
    @SerialName("user")
    USER,
    @SerialName("assistant")
    ASSISTANT
}