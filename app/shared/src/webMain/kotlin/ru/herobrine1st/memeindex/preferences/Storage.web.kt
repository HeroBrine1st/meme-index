package ru.herobrine1st.memeindex.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage

actual fun createPreferencesStorage(): OkioStorage<Preferences> {
    TODO("Store in session storage, setting base server path to something compiled-in")
}

// No storage yet, so there is nothing to read: the server base path is
// always unconfigured on web until storage lands.
internal actual fun readPreferencesBlocking(dataStore: DataStore<Preferences>): Preferences = Preferences()
