package org.megras.graphstore

import io.grpc.ManagedChannelBuilder
import org.megras.data.graph.*
import org.vitrivr.cottontail.client.SimpleClient
import org.vitrivr.cottontail.client.language.basics.Type
import org.vitrivr.cottontail.client.language.basics.predicate.And
import org.vitrivr.cottontail.client.language.basics.predicate.Expression
import org.vitrivr.cottontail.client.language.ddl.CreateEntity
import org.vitrivr.cottontail.client.language.ddl.CreateIndex
import org.vitrivr.cottontail.client.language.ddl.CreateSchema
import org.vitrivr.cottontail.client.language.dml.Insert
import org.vitrivr.cottontail.client.language.dql.Query
import org.vitrivr.cottontail.grpc.CottontailGrpc
import java.lang.IllegalStateException


class CottontailStore(host: String = "localhost", port: Int = 1865) {

    //TODO make configurable
    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

    private val client = SimpleClient(channel)


    private companion object {
        const val LONG_LITERAL_TYPE = -1
        const val DOUBLE_LITERAL_TYPE = -2
        const val STRING_LITERAL_TYPE = -3
        const val BINARY_DATA_TYPE = 0
    }

    fun setup() {

        //TODO check if exists before create

        client.create(CreateSchema("megras"))

        client.create(
            CreateEntity("megras.quads")
                .column("id", Type.LONG, autoIncrement = true)
                .column("s_type", Type.INTEGER)
                .column("s", Type.LONG)
                .column("p_type", Type.INTEGER)
                .column("p", Type.LONG)
                .column("o_type", Type.INTEGER)
                .column("o", Type.LONG)
        )

        client.create(CreateIndex("megras.quads", "id", CottontailGrpc.IndexType.BTREE_UQ))
        client.create(CreateIndex("megras.quads", "s_type", CottontailGrpc.IndexType.BTREE))
        client.create(CreateIndex("megras.quads", "s", CottontailGrpc.IndexType.BTREE))
        client.create(CreateIndex("megras.quads", "p_type", CottontailGrpc.IndexType.BTREE))
        client.create(CreateIndex("megras.quads", "p", CottontailGrpc.IndexType.BTREE))
        client.create(CreateIndex("megras.quads", "o_type", CottontailGrpc.IndexType.BTREE))
        client.create(CreateIndex("megras.quads", "o", CottontailGrpc.IndexType.BTREE))

        client.create(
            CreateEntity("megras.literal_string")
                .column("id", Type.LONG, autoIncrement = true)
                .column("value", Type.STRING)
        )

        client.create(CreateIndex("megras.literal_string", "id", CottontailGrpc.IndexType.BTREE_UQ))
        client.create(CreateIndex("megras.literal_string", "value", CottontailGrpc.IndexType.BTREE))

        client.create(
            CreateEntity("megras.literal_double")
                .column("id", Type.LONG, autoIncrement = true)
                .column("value", Type.DOUBLE)
        )

        client.create(CreateIndex("megras.literal_double", "id", CottontailGrpc.IndexType.BTREE_UQ))
        client.create(CreateIndex("megras.literal_double", "value", CottontailGrpc.IndexType.BTREE))

        client.create(
            CreateEntity("megras.entity_prefix")
                .column("id", Type.INTEGER, autoIncrement = true)
                .column("prefix", Type.STRING)
        )

        client.create(CreateIndex("megras.entity_prefix", "id", CottontailGrpc.IndexType.BTREE_UQ))
        client.create(CreateIndex("megras.entity_prefix", "prefix", CottontailGrpc.IndexType.BTREE))

        client.create(
            CreateEntity("megras.entity")
                .column("id", Type.LONG, autoIncrement = true)
//                .column("prefix", Type.INTEGER)
                .column("value", Type.STRING)
        )

        client.create(CreateIndex("megras.entity", "id", CottontailGrpc.IndexType.BTREE_UQ))
        client.create(CreateIndex("megras.entity", "value", CottontailGrpc.IndexType.BTREE))

//        client.create(CreateEntity("type_map")
//            .column("id", Type.INTEGER, autoIncrement = true)
//            .column("type", Type.STRING)
//        )



    }

    private fun getQuadValueId(quadValue: QuadValue): Pair<Int?, Long?> {

        return when (quadValue) {
            is DoubleValue -> DOUBLE_LITERAL_TYPE to getDoubleLiteralId(quadValue.value)
            is LongValue -> LONG_LITERAL_TYPE to quadValue.value //no indirection needed
            is StringValue -> STRING_LITERAL_TYPE to getStringLiteralId(quadValue.value)
            is URIValue -> getUriValueId(quadValue)
        }

    }

    private fun getOrAddQuadValueId(quadValue: QuadValue): Pair<Int, Long> {

        return when (quadValue) {
            is DoubleValue -> DOUBLE_LITERAL_TYPE to getOrAddDoubleLiteralId(quadValue.value)
            is LongValue -> LONG_LITERAL_TYPE to quadValue.value //no indirection needed
            is StringValue -> STRING_LITERAL_TYPE to getOrAddStringLiteralId(quadValue.value)
            is URIValue -> getOrAddUriValueId(quadValue)
        }

    }

    private fun getDoubleLiteralId(value: Double): Long? {
        val result = client.query(
            Query("megras.literal_double").select("id").where(
                Expression("value", "=", value)
            )
        )

        if (result.hasNext()) {
            val tuple = result.next()
            return tuple.asLong("id")
        }

        return null
    }

    /**
     * Retrieves id of existing double literal or creates new one
     */
    private fun getOrAddDoubleLiteralId(value: Double): Long {

        val id = getDoubleLiteralId(value)

        if (id != null) {
            return id
        }

        //value not yet present, inserting new
        client.insert(
            Insert("megras.literal_double").value("value", value)
        )

        return getDoubleLiteralId(value) ?: throw IllegalStateException("could not obtain id for inserted value")

    }

    private fun getStringLiteralId(value: String): Long? {
        val result = client.query(
            Query("megras.literal_string").select("id").where(
                Expression("value", "=", value)
            )
        )

        if (result.hasNext()) {
            val tuple = result.next()
            return tuple.asLong("id")
        }

        return null
    }

    /**
     * Retrieves id of existing string literal or creates new one
     */
    private fun getOrAddStringLiteralId(value: String): Long {

        val id = getStringLiteralId(value)

        if (id != null) {
            return id
        }

        //value not yet present, inserting new
        client.insert(
            Insert("megras.literal_string").value("value", value)
        )

        return getStringLiteralId(value) ?: throw IllegalStateException("could not obtain id for inserted value")

    }

    private fun getUriValueId(value: URIValue): Pair<Int?, Long?> {

        fun prefix(value: String): Int? {
            val result = client.query(
                Query("megras.entity_prefix").select("id").where(
                    Expression("prefix", "=", value)
                )
            )

            if (result.hasNext()) {
                val tuple = result.next()
                return tuple.asInt("id")
            }

            return null
        }

        fun suffix(value: String): Long? {
            val result = client.query(
                Query("megras.entity").select("id").where(
                    Expression("value", "=", value)
                )
            )

            if (result.hasNext()) {
                val tuple = result.next()
                return tuple.asLong("id")
            }

            return null
        }

        return prefix(value.prefix()) to suffix(value.suffix())

    }

    private fun getOrAddUriValueId(value: URIValue): Pair<Int, Long> {

        val (prefix, suffix) = getUriValueId(value)

        if (prefix == null) {
            client.insert(
                Insert("megras.entity_prefix").value("prefix", value.prefix())
            )
        }

        if (suffix == null) {
            client.insert(
                Insert("megras.entity").value("value", value.suffix())
            )
        }

        if (prefix != null && suffix != null) {
            return prefix to suffix
        }

        val pair = getUriValueId(value)

        if (pair.first == null || pair.second == null) {
            throw IllegalStateException("could not obtain id for inserted value")
        }

        return pair.first!! to pair.second!!

    }

    private fun getQuadId(subject: Pair<Int, Long>, predicate: Pair<Int, Long>, `object`: Pair<Int, Long>): Long? {
        val result = client.query(
            Query("megras.quads")
                .select("id")
                .where(
                    And(
                        And(
                            And(
                                Expression("s_type", "=", subject.first),
                                Expression("s", "=", subject.second)
                            ),
                            And(
                                Expression("p_type", "=", predicate.first),
                                Expression("p", "=", predicate.second)
                            )
                        ),
                        And(
                            Expression("o_type", "=", `object`.first),
                            Expression("o", "=", `object`.second)
                        )
                    )
                )
        )
        if (result.hasNext()) {
            return result.next().asLong("id")
        }
        return null
    }

    /**
     * Stores a Quad if it doesn't already exist and returns its id
     */
    fun addQuad(quad: Quad): Long {

        val s = getOrAddQuadValueId(quad.subject)
        val p = getOrAddQuadValueId(quad.predicate)
        val o = getOrAddQuadValueId(quad.`object`)

        val existingId = getQuadId(s, p, o)

        if (existingId != null) {
            return existingId
        }

        client.insert(Insert("megras.quads")
            .value("s_type", s.first)
            .value("s", s.second)
            .value("p_type", p.first)
            .value("p", p.second)
            .value("o_type", o.first)
            .value("o", o.second)
        )

        return getQuadId(s, p, o) ?: throw IllegalStateException("could not obtain id for inserted value")

    }

}