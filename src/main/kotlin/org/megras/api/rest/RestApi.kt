package org.megras.api.rest

import io.javalin.Javalin
import org.megras.api.rest.handlers.RawObjectRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.model.Config
import io.javalin.apibuilder.ApiBuilder.*
import org.megras.api.rest.handlers.CachedSegmentRequestHandler
import org.megras.api.rest.handlers.CanonicalObjectRequestHandler
import org.megras.api.rest.handlers.CanonicalSegmentRequestHandler
import org.megras.graphstore.MutableQuadSet

object RestApi {

    private var javalin: Javalin? = null

    fun init(config: Config, objectStore: FileSystemObjectStore, quadSet: MutableQuadSet) {

        if (javalin != null) {
            stop() //stop instance in case there already is one. should not happen, just in case
        }

        val rawObjectRequestHandler = RawObjectRequestHandler(objectStore)
        val canonicalObjectRequestHandler = CanonicalObjectRequestHandler(quadSet, objectStore)
        val cachedSegmentRequestHandler = CachedSegmentRequestHandler(quadSet, objectStore)
        val canonicalSegmentRequestHandler = CanonicalSegmentRequestHandler(quadSet, objectStore)


        javalin = Javalin.create {
            it.enableCorsForAllOrigins()
            it.showJavalinBanner = false
        }.routes {
            get("/raw/{objectId}", rawObjectRequestHandler::get)
            get("/{objectId}", canonicalObjectRequestHandler::get)
            get("/{objectId}/c/{segmentId}", cachedSegmentRequestHandler::get)
            get("/{objectId}/segment/{segmentation}/<segmentDefinition>", canonicalSegmentRequestHandler::get)
        }.exception(RestErrorStatus::class.java) { e, ctx ->
            ctx.status(e.statusCode)
            ctx.result(e.message)
        }.start(config.httpPort)
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}