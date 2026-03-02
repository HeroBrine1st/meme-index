package ru.herobrine1st.memeindex.shared.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class Vector(public val values: List<Float>)

public fun List<Float>.toVector(): Vector = Vector(this)