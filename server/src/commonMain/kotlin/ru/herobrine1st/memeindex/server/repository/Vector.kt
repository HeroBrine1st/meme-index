package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.ValueEncoder
import ru.herobrine1st.memeindex.shared.model.Vector

object VectorEncoder : ValueEncoder<Vector> {
    override fun encode(value: Vector): Any =
        value.values.joinToString(separator = ",", prefix = "[", postfix = "]")

    override fun decode(value: ResultSet.Row.Column): Vector {
        val elements = value.asString().removeSurrounding("[", "]").split(",")
        return Vector(elements.map { it.trim().toFloat() })
    }
}
