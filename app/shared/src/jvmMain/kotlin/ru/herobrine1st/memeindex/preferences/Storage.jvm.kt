package ru.herobrine1st.memeindex.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

actual fun createPreferencesStorage(): OkioStorage<Preferences> {
    return OkioStorage(
        FileSystem.SYSTEM,
        PreferencesSerializer(),
        producePath = { FileKit.databasesDir.resolve("preferences.pb").path.toPath() }
    )
}
internal actual fun readPreferencesBlocking(dataStore: DataStore<Preferences>): Preferences =
    runBlocking { dataStore.data.first() }
