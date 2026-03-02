package ru.herobrine1st.memeindex.server.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.debounceIntoBuffer(timeoutMillis: Long): Flow<List<T>> = flow {
    require(timeoutMillis > 0)

    val batch = mutableListOf<T>()

    this@debounceIntoBuffer
        .transformLatest { item ->
            batch += item
            delay(timeoutMillis.milliseconds)
            val snapshot = batch.toList()
            batch.clear()
            try {
                emit(snapshot)
            } catch (e: CancellationException) {
                batch.addAll(snapshot) // at least once delivery guaranteed
                throw e
            }
        }
        .collect { emit(it) }
}