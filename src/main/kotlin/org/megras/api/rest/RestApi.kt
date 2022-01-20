package org.megras.api.rest

import io.javalin.Javalin
import org.megras.api.rest.handlers.RawObjectRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.model.Config
import io.javalin.apibuilder.ApiBuilder.*

object RestApi {

    private var javalin: Javalin? = null

    fun init(config: Config, objectStore: FileSystemObjectStore) {

        val rawObjectRequestHandler = RawObjectRequestHandler(objectStore)


        javalin = Javalin.create {
            it.enableCorsForAllOrigins()
            it.showJavalinBanner = false
        }.routes {
            get("/raw/{objectId}", rawObjectRequestHandler::get)
        }.start(config.httpPort)
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}