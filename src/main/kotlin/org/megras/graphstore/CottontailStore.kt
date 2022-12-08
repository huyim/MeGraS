package org.megras.graphstore

import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.serialization.descriptors.mapSerialDescriptor
import org.megras.data.graph.*
import org.vitrivr.cottontail.client.SimpleClient
import org.vitrivr.cottontail.client.language.basics.Type
import org.vitrivr.cottontail.client.language.basics.predicate.And
import org.vitrivr.cottontail.client.language.basics.predicate.Expression
import org.vitrivr.cottontail.client.language.basics.predicate.Or
import org.vitrivr.cottontail.client.language.basics.predicate.Predicate
import org.vitrivr.cottontail.client.language.ddl.CreateEntity
import org.vitrivr.cottontail.client.language.ddl.CreateIndex
import org.vitrivr.cottontail.client.language.ddl.CreateSchema
import org.vitrivr.cottontail.client.language.dml.Insert
import org.vitrivr.cottontail.client.language.dql.Query
import org.vitrivr.cottontail.grpc.CottontailGrpc
import java.lang.IllegalStateException


class CottontailStore(host: String = "localhost", port: Int = 1865) : MutableQuadSet {

    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

    private val client = SimpleClient(channel)


    private companion object {
        const val LOCAL_URI_TYPE = -1
        const val LONG_LITERAL_TYPE = -2
        const val DOUBLE_LITERAL_TYPE = -3
        const val STRING_LITERAL_TYPE = -4
        const val BINARY_DATA_TYPE = 0
    }

    fun setup() {

        fun catchExists(lambda: () -> Unit) {
            try {
                lambda()
            } catch (e: StatusRuntimeException) {
                if (e.message?.contains("ALREADY_EXISTS") == false) {
                    throw e
                }
            }
        }

        catchExists { client.create(CreateSchema("megras")) }

        catchExists {
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
        }

        catchExists { client.create(CreateIndex("megras.quads", "id", CottontailGrpc.IndexType.BTREE_UQ)) }
        catchExists { client.create(CreateIndex("megras.quads", "s_type", CottontailGrpc.IndexType.BTREE)) }
        catchExists { client.create(CreateIndex("megras.quads", "s", CottontailGrpc.IndexType.BTREE)) }
        catchExists { client.create(CreateIndex("megras.quads", "p_type", CottontailGrpc.IndexType.BTREE)) }
        catchExists { client.create(CreateIndex("megras.quads", "p", CottontailGrpc.IndexType.BTREE)) }
        catchExists { client.create(CreateIndex("megras.quads", "o_type", CottontailGrpc.IndexType.BTREE)) }
        catchExists { client.create(CreateIndex("megras.quads", "o", CottontailGrpc.IndexType.BTREE)) }

        catchExists {
            client.create(
                CreateEntity("megras.literal_string")
                    .column("id", Type.LONG, autoIncrement = true)
                    .column("value", Type.STRING)
            )
        }

        catchExists{ client.create(CreateIndex("megras.literal_string", "id", CottontailGrpc.IndexType.BTREE_UQ)) }
        catchExists{ client.create(CreateIndex("megras.literal_string", "value", CottontailGrpc.IndexType.BTREE)) }

        catchExists{
            client.create(
                CreateEntity("megras.literal_double")
                    .column("id", Type.LONG, autoIncrement = true)
                    .column("value", Type.DOUBLE)
            )
        }

        catchExists{ client.create(CreateIndex("megras.literal_double", "id", CottontailGrpc.IndexType.BTREE_UQ)) }
        catchExists{ client.create(CreateIndex("megras.literal_double", "value", CottontailGrpc.IndexType.BTREE)) }

        catchExists{
            client.create(
                CreateEntity("megras.entity_prefix")
                    .column("id", Type.INTEGER, autoIncrement = true)
                    .column("prefix", Type.STRING)
            )
        }

        catchExists{ client.create(CreateIndex("megras.entity_prefix", "id", CottontailGrpc.IndexType.BTREE_UQ)) }
        catchExists{ client.create(CreateIndex("megras.entity_prefix", "prefix", CottontailGrpc.IndexType.BTREE)) }

        catchExists{
            client.create(
                CreateEntity("megras.entity")
                    .column("id", Type.LONG, autoIncrement = true)
//                .column("prefix", Type.INTEGER)
                    .column("value", Type.STRING)
            )
        }

        catchExists{ client.create(CreateIndex("megras.entity", "id", CottontailGrpc.IndexType.BTREE_UQ)) }
        catchExists{ client.create(CreateIndex("megras.entity", "value", CottontailGrpc.IndexType.BTREE)) }

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

    private fun getQuadValue(type: Int, id: Long): QuadValue? {

        return when (type) {
            DOUBLE_LITERAL_TYPE -> getDoubleValue(id)
            LONG_LITERAL_TYPE -> LongValue(id)
            STRING_LITERAL_TYPE -> getStringValue(id)
            else -> getUriValue(type, id)
        }

    }

    private fun getDoubleValue(id: Long): DoubleValue? {
        val result = client.query(
            Query("megras.literal_double")
                .select("value")
                .where(Expression("id", "=", id))
        )

        if (result.hasNext()) {
            val value = result.next().asDouble("value")
            if (value != null) {
                return DoubleValue(value)
            }
        }

        return null
    }

    private fun getStringValue(id: Long): StringValue? {
        val result = client.query(
            Query("megras.literal_string")
                .select("value")
                .where(Expression("id", "=", id))
        )

        if (result.hasNext()) {
            val value = result.next().asString("value")
            if (value != null) {
                return StringValue(value)
            }
        }

        return null
    }

    private fun getUriValue(type: Int, id: Long): URIValue? {

        fun prefix(id: Int): String? {
            val result = client.query(
                Query("megras.entity_prefix").select("prefix").where(
                    Expression("id", "=", id)
                )
            )

            if (result.hasNext()) {
                val tuple = result.next()
                return tuple.asString("prefix")
            }

            return null
        }

        fun suffix(id: Long): String? {
            val result = client.query(
                Query("megras.entity").select("value").where(
                    Expression("id", "=", id)
                )
            )

            if (result.hasNext()) {
                val tuple = result.next()
                return tuple.asString("value")
            }

            return null
        }

        if (type == LOCAL_URI_TYPE) {
            val suffix = suffix(id) ?: return null
            return LocalQuadValue(suffix)
        }

        val prefix = prefix(type) ?: return null
        val suffix = suffix(id) ?: return null

        return URIValue(prefix, suffix)
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

        if (value is LocalQuadValue || value.prefix() == LocalQuadValue.defaultPrefix) {
            return LOCAL_URI_TYPE to suffix(value.suffix())
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

    private fun filterExpression(column: String, type: Int, id: Long) = And(
        Expression("${column}_type", "=", type),
        Expression(column, "=", id)
    )

    private fun subjectFilterExpression(type: Int, id: Long) = filterExpression("s", type, id)
    private fun predicateFilterExpression(type: Int, id: Long) = filterExpression("p", type, id)
    private fun objectFilterExpression(type: Int, id: Long) = filterExpression("o", type, id)

    private fun getQuadId(subject: Pair<Int, Long>, predicate: Pair<Int, Long>, `object`: Pair<Int, Long>): Long? {
        val result = client.query(
            Query("megras.quads")
                .select("id")
                .where(
                    And(
                        And(
                            subjectFilterExpression(subject.first, subject.second),
                            predicateFilterExpression(predicate.first, predicate.second)
                        ),
                        objectFilterExpression(`object`.first, `object`.second)
                    )
                )
        )
        if (result.hasNext()) {
            return result.next().asLong("id")
        }
        return null
    }

    private fun insert(sType: Int, s: Long, pType: Int, p: Long, oType: Int, o: Long) {
        client.insert(
            Insert("megras.quads")
                .value("s_type", sType)
                .value("s", s)
                .value("p_type", pType)
                .value("p", p)
                .value("o_type", oType)
                .value("o", o)
        )
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

        insert(s.first, s.second, p.first, p.second, o.first, o.second)

        return getQuadId(s, p, o) ?: throw IllegalStateException("could not obtain id for inserted value")

    }

    override fun getId(id: Long): Quad? {

        val result = client.query(
            Query("megras.quads")
                .select("*")
                .where(Expression("id", "=", id))
        )

        if (!result.hasNext()) {
            return null
        }

        val tuple = result.next()

        val s = getQuadValue(tuple.asInt("s_type")!!, tuple.asLong("s")!!) ?: return null
        val p = getQuadValue(tuple.asInt("p_type")!!, tuple.asLong("p")!!) ?: return null
        val o = getQuadValue(tuple.asInt("o_type")!!, tuple.asLong("o")!!) ?: return null

        return Quad(id, s, p, o)
    }


    override fun filterSubject(subject: QuadValue): QuadSet {

        val s = getQuadValueId(subject)

        if (s.first == null || s.second == null) { //no match, no results
            return BasicQuadSet() //return empty set
        }

        val result = client.query(
            Query("megras.quads")
                .select("id")
                .where(
                    subjectFilterExpression(s.first!!, s.second!!)
                )
        )

        val quadSet = BasicMutableQuadSet()
        while (result.hasNext()) {
            val id = result.next().asLong("id") ?: continue
            val quad = getId(id) ?: continue
            quadSet.add(quad)
        }

        return quadSet
    }

    override fun filterPredicate(predicate: QuadValue): QuadSet {
        val p = getQuadValueId(predicate)

        if (p.first == null || p.second == null) { //no match, no results
            return BasicQuadSet() //return empty set
        }

        val result = client.query(
            Query("megras.quads")
                .select("id")
                .where(
                    predicateFilterExpression(p.first!!, p.second!!)
                )
        )

        val quadSet = BasicMutableQuadSet()
        while (result.hasNext()) {
            val id = result.next().asLong("id") ?: continue
            val quad = getId(id) ?: continue
            quadSet.add(quad)
        }

        return quadSet
    }

    override fun filterObject(`object`: QuadValue): QuadSet {
        val o = getQuadValueId(`object`)

        if (o.first == null || o.second == null) { //no match, no results
            return BasicQuadSet() //return empty set
        }

        val result = client.query(
            Query("megras.quads")
                .select("id")
                .where(
                    objectFilterExpression(o.first!!, o.second!!)
                )
        )

        val quadSet = BasicMutableQuadSet()
        while (result.hasNext()) {
            val id = result.next().asLong("id") ?: continue
            val quad = getId(id) ?: continue
            quadSet.add(quad)
        }

        return quadSet
    }

    override fun filter(
        subjects: Collection<QuadValue>?,
        predicates: Collection<QuadValue>?,
        objects: Collection<QuadValue>?
    ): QuadSet {

        //if all attributes are unfiltered, do not filter
        if (subjects == null && predicates == null && objects == null) {
            return this
        }

        //if one attribute has no matches, return empty set
        if (subjects?.isEmpty() == true || predicates?.isEmpty() == true || objects?.isEmpty() == true) {
            return BasicQuadSet()
        }

        val filterIds =
            ((subjects?.toSet() ?: setOf()) + (predicates?.toSet() ?: setOf()) + (objects?.toSet() ?: setOf()))
                .map { it to getQuadValueId(it) }
                .mapNotNull { if (it.second.first == null || it.second.second == null) null else it.first to (it.second.first!! to it.second.second!!) } //remove values that were not found
                .toMap()

        val subjectFilterIds = subjects?.mapNotNull { filterIds[it] }
        val predicateFilterIds = predicates?.mapNotNull { filterIds[it] }
        val objectFilterIds = objects?.mapNotNull { filterIds[it] }

        //no matching values
        if (subjectFilterIds?.isEmpty() == true || predicateFilterIds?.isEmpty() == true || objectFilterIds?.isEmpty() == true) {
            return BasicQuadSet()
        }

        fun select(predicates: Collection<Predicate>): Set<Long> {
            if (predicates.isEmpty()) {
                return emptySet()
            }

            val predicate = predicates.reduce { acc, predicate -> Or(acc, predicate) }

            val result = client.query(
                Query("megras.quads")
                    .select("id")
                    .where(predicate)
            )

            val ids = mutableSetOf<Long>()

            while (result.hasNext()) {
                val id = result.next().asLong("id") ?: continue
                ids.add(id)
            }

            return ids

        }

        var selectedIds: Set<Long>? = null

        if (subjectFilterIds != null) {
            selectedIds = select(subjectFilterIds.map { subjectFilterExpression(it.first, it.second) })
        }

        if (predicateFilterIds != null) {
            val ids = select(predicateFilterIds.map { predicateFilterExpression(it.first, it.second) })
            selectedIds = selectedIds?.intersect(ids) ?: ids
        }

        if (objectFilterIds != null) {
            val ids = select(objectFilterIds.map { objectFilterExpression(it.first, it.second) })
            selectedIds = selectedIds?.intersect(ids) ?: ids
        }

        if (selectedIds.isNullOrEmpty()) { //should never be null
            return BasicQuadSet()
        }

        return BasicMutableQuadSet(selectedIds.mapNotNull { getId(it) }.toMutableSet())
    }

    override fun toMutable(): MutableQuadSet = this

    override fun toSet(): Set<Quad> {
        TODO("Not yet implemented")
    }

    override fun plus(other: QuadSet): QuadSet {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")

    override fun contains(element: Quad): Boolean {
        val s = getQuadValueId(element.subject)

        if (s.first == null || s.second == null) {
            return false
        }

        val p = getQuadValueId(element.predicate)

        if (p.first == null || p.second == null) {
            return false
        }

        val o = getQuadValueId(element.`object`)

        if (o.first == null || o.second == null) {
            return false
        }

        return getQuadId(s.first!! to s.second!!, p.first!! to p.second!!, o.first!! to o.second!!) != null
    }

    override fun containsAll(elements: Collection<Quad>): Boolean {
        return elements.all { contains(it) }
    }

    override fun isEmpty(): Boolean = this.size == 0

    override fun iterator(): MutableIterator<Quad> {
        TODO("Not yet implemented")
    }

    override fun add(element: Quad): Boolean {

        val s = getOrAddQuadValueId(element.subject)
        val p = getOrAddQuadValueId(element.predicate)
        val o = getOrAddQuadValueId(element.`object`)

        val existingId = getQuadId(s, p, o)

        if (existingId != null) {
            return false
        }

        insert(s.first, s.second, p.first, p.second, o.first, o.second)

        return true
    }

    override fun addAll(elements: Collection<Quad>): Boolean {
        return elements.map { add(it) }.any()
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun remove(element: Quad): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<Quad>): Boolean {
        return elements.map { remove(it) }.any()
    }

    override fun retainAll(elements: Collection<Quad>): Boolean {
        TODO("Not yet implemented")
    }

}