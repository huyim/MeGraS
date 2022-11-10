package org.megras.graphstore

import io.grpc.ManagedChannelBuilder
import org.vitrivr.cottontail.client.SimpleClient
import org.vitrivr.cottontail.client.language.basics.Type
import org.vitrivr.cottontail.client.language.ddl.CreateEntity
import org.vitrivr.cottontail.client.language.ddl.CreateSchema

class CottontailStore {

    //TODO make configurable
    private val channel = ManagedChannelBuilder.forAddress("127.0.0.1", 1865).usePlaintext().build()

    private val client = SimpleClient(channel)

    private fun setup() {

        client.create(CreateSchema("megras"))

        client.create(CreateEntity("triples")
            .column("id", Type.LONG, autoIncrement = true)
            .column("s_type", Type.INTEGER)
            .column("s", Type.LONG)
            .column("p_type", Type.INTEGER)
            .column("p", Type.LONG)
            .column("o_type", Type.INTEGER)
            .column("o", Type.LONG)
        )

        client.create(CreateEntity("literal_string")
            .column("id", Type.LONG, autoIncrement = true)
            .column("value", Type.STRING)
        )

        client.create(CreateEntity("literal_double")
            .column("id", Type.LONG, autoIncrement = true)
            .column("value", Type.DOUBLE)
        )

        client.create(CreateEntity("entity_prefix")
            .column("id", Type.INTEGER, autoIncrement = true)
            .column("prefix", Type.STRING)
        )

        client.create(CreateEntity("entity")
            .column("id", Type.LONG, autoIncrement = true)
            .column("prefix", Type.INTEGER)
            .column("value", Type.STRING)
        )

    }



}