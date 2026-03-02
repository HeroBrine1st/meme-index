package ru.herobrine1st.memeindex.preferences

import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Preferences(
    @ProtoNumber(1) val serverBasePath: Url? = null
)
