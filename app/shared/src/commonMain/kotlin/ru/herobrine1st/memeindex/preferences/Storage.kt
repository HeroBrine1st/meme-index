package ru.herobrine1st.memeindex.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage

expect fun createPreferencesStorage(): OkioStorage<Preferences>

/**
 * One synchronous read at startup: it picks the initial destination
 * (server configuration screen when no base path is stored). Not available
 * on web - the web storage is not implemented yet.
 */
internal expect fun readPreferencesBlocking(dataStore: DataStore<Preferences>): Preferences