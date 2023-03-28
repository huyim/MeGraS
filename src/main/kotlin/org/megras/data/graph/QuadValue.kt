package org.megras.data.graph

import java.net.URI
import java.net.URISyntaxException

sealed class QuadValue {

    companion object {

        fun of(value: Any) = when (value) {
            is Number -> {
                if (value is Float || value is Double) {
                    of(value.toDouble())
                } else {
                    of(value.toLong())
                }
            }

            is String -> of(value)
            is DoubleArray -> of(value)
            is LongArray -> of(value)
            else -> of(value.toString())
        }

        fun of(value: String) = when {
            value.startsWith('<') && value.endsWith('>') -> if(value.startsWith("<${LocalQuadValue.defaultPrefix}")) {
                LocalQuadValue(value.substringAfter(LocalQuadValue.defaultPrefix).substringBeforeLast('>'))
            } else {
                URIValue(value)
            }
            value.endsWith("^^String") -> StringValue(value.substringBeforeLast("^^String"))
            value.endsWith("^^Long") -> LongValue(value.substringAfterLast("^^Long").toLongOrNull() ?: 0L)
            value.endsWith("^^Double") -> DoubleValue(value.substringAfterLast("^^Double").toDoubleOrNull() ?: Double.NaN)
            //TODO parse vectors
            else -> StringValue(value)
        }
        fun of(value: Long) = LongValue(value)
        fun of(value: Double) = DoubleValue(value)
        fun of(prefix: String, uri: String) = URIValue(prefix, uri)
        fun of(value: DoubleArray) = DoubleVectorValue(value)
        fun of(value: List<Double>) = DoubleVectorValue(value)
        fun of(value: LongArray) = LongVectorValue(value)
        fun of(value: List<Long>) = LongVectorValue(value)


    }

}

data class StringValue(val value: String): QuadValue() {
    override fun toString(): String = "$value^^String"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringValue

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
data class LongValue(val value: Long): QuadValue() {
    override fun toString(): String = "$value^^Long"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LongValue

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
data class DoubleValue(val value: Double): QuadValue() {
    override fun toString(): String = "$value^^Double"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DoubleValue

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

open class URIValue(private val prefix: String?, protected open val uri: String) : QuadValue() {

    companion object {
        private fun estimatePrefix(uri: String): Pair<String, String> {

            val cleaned = if (uri.startsWith('<') && uri.endsWith('>')) {
                uri.substring(1).substringBeforeLast('>')
            } else {
                uri
            }

            //best effort prefix estimator
            return try {
                val parsedUri = URI(uri)
                val host = parsedUri.host ?: ""
                val suffix = uri.substringAfter(host)
                val prefix = uri.substringBefore(suffix)
                prefix to suffix
            }catch (e: URISyntaxException) {
                "" to cleaned
            }

        }
    }

    private constructor(pair: Pair<String, String>) : this(pair.first, pair.second)
    constructor(uri: String) : this(estimatePrefix(uri))

    override fun toString() = "<${prefix}${uri}>"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as URIValue

        return other.toString() == toString()
    }

    override fun hashCode(): Int = toString().hashCode()

    open fun prefix(): String = prefix ?: ""
    open fun suffix(): String = uri

}

open class VectorValue(val type: Type, val length: Int) : QuadValue() {

    enum class Type {
        Double, Long
    }

}

class DoubleVectorValue(val vector: DoubleArray) : VectorValue(Type.Double, vector.size) {
    constructor(values: List<Double>) : this(values.toDoubleArray())
//    constructor(values: List<Float>) : this(DoubleArray(values.size) { i -> values[i].toDouble() })
//    constructor(values: FloatArray) : this(DoubleArray(values.size) { i -> values[i].toDouble() })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DoubleVectorValue

        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        return vector.contentHashCode()
    }
}

class LongVectorValue(val vector: LongArray) : VectorValue(Type.Long, vector.size) {

    constructor(values: List<Long>) : this(values.toLongArray())
//    constructor(values: List<Int>) : this(LongArray(values.size) { i -> values[i].toLong() })
//    constructor(values: IntArray) : this(LongArray(values.size) { i -> values[i].toLong() })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LongVectorValue

        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        return vector.contentHashCode()
    }
}