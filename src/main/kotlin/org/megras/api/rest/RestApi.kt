package org.megras.api.rest

import io.javalin.Javalin
import org.megras.api.rest.handlers.RawObjectRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.model.Config
import io.javalin.apibuilder.ApiBuilder.*
import org.megras.api.rest.handlers.CachedSegmentRequestHandler
import org.megras.api.rest.handlers.CanonicalObjectRequestHandler
import org.megras.api.rest.handlers.CanonicalSegmentRequestHandler

object RestApi {

    private var javalin: Javalin? = null

    fun init(config: Config, objectStore: FileSystemObjectStore) {

        if (javalin != null) {
            stop() //stop instance in case there already is one. should not happen, just in case
        }

        val rawObjectRequestHandler = RawObjectRequestHandler(objectStore)
        val canonicalObjectRequestHandler = CanonicalObjectRequestHandler()
        val cachedSegmentRequestHandler = CachedSegmentRequestHandler()
        val canonicalSegmentRequestHandler = CanonicalSegmentRequestHandler()


        javalin = Javalin.create {
            it.enableCorsForAllOrigins()
            it.showJavalinBanner = false
        }.routes {
            get("/raw/{objectId}", rawObjectRequestHandler::get)
            get("/{objectId}", canonicalObjectRequestHandler::get)
            get("/{objectId}/c/{segmentId}", cachedSegmentRequestHandler::get)
            get("/{objectId}/segment/{segmentation}/<segmentDefinition>", canonicalSegmentRequestHandler::get)
        }.start(config.httpPort)
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}