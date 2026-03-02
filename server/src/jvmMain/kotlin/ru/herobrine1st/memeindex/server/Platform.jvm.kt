package ru.herobrine1st.memeindex.server

import kotlinx.io.bytestring.ByteString
import java.security.MessageDigest

actual fun sha256(data: ByteString): ByteString = ByteString(
    MessageDigest.getInstance("SHA-256")
        .digest(data.toByteArray())
)