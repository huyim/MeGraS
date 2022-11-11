package org.megras.data.graph

sealed class QuadValue {

    companion object {

        fun of(value: Any) = when {
            value is Number -> {
                if (value is Float || value is Double) {
                    of(value.toDouble())
                } else {
                    of(value.toLong())
                }
            }
            value is String -> of(value)
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
            else -> StringValue(value)
        }
        fun of(value: Long) = LongValue(value)
        fun of(value: Double) = DoubleValue(value)
        fun of(prefix: String, uri: String) = URIValue(prefix, uri)

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

            //TODO best effort prefix estimator

            return "" to cleaned
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

}

