package ru.herobrine1st.memeindex.util

import kotlinx.coroutines.MainCoroutineDispatcher
import kotlin.concurrent.Volatile


// From Decompose
@Volatile
private var isImmediateSupported: Boolean = true

internal val MainCoroutineDispatcher.immediateOrFallback: MainCoroutineDispatcher
    get() {
        if (isImmediateSupported) {
            try {
                return immediate
            } catch (_: UnsupportedOperationException) {
            } catch (_: NotImplementedError) {
            }

            isImmediateSupported = false
        }

        return this
    }