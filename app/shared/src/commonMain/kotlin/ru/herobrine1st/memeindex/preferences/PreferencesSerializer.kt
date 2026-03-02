package ru.herobrine1st.memeindex.preferences

import androidx.datastore.core.okio.OkioSerializer
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@OptIn(ExperimentalSerializationApi::class)
class PreferencesSerializer(
    val overrideServerBasePath: Url? = null,
) : OkioSerializer<Preferences> {
    override val defaultValue: Preferences
        get() = Preferences(serverBasePath = overrideServerBasePath)

    override suspend fun readFrom(source: BufferedSource): Preferences {
        return protobuf.decodeFromByteArray<Preferences>(source.readByteArray()).let {
            // override base path for web
            if (overrideServerBasePath != null) it.copy(serverBasePath = overrideServerBasePath)
            else it
        }
    }

    override suspend fun writeTo(t: Preferences, sink: BufferedSink) {
        sink.write(protobuf.encodeToByteArray(t))
    }

    companion object {
        private val protobuf = ProtoBuf {}
    }
}