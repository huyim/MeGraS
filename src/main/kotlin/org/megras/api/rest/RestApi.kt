package org.megras.api.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import org.megras.api.rest.handlers.*
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.model.Config
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
        val aboutObjectRequestHandler = AboutObjectRequestHandler(quadSet, objectStore)
        val addFileRequestHandler = AddFileRequestHandler(quadSet, objectStore)


        javalin = Javalin.create {
            it.plugins.enableCors { cors ->
                cors.add { corsPluginConfig ->
                    corsPluginConfig.anyHost()
                }
            }
            it.showJavalinBanner = false
        }.routes {
            get("/raw/{objectId}", rawObjectRequestHandler::get)
            get("/{objectId}", canonicalObjectRequestHandler::get)
            get("/<objectId>/about", aboutObjectRequestHandler::get)
            get("/{objectId}/segment/{segmentation}/{segmentDefinition}/segment/{nextSegmentation}/{nextSegmentDefinition}/<tail>", canonicalSegmentRequestHandler::get)
            get("/{objectId}/segment/{segmentation}/{segmentDefinition}/segment/{nextSegmentation}/{nextSegmentDefinition}", canonicalSegmentRequestHandler::get)
            get("/{objectId}/segment/{segmentation}/{segmentDefinition}", canonicalSegmentRequestHandler::get)
            get("/{objectId}/c/{segmentId}/segment/{segmentation}/{segmentDefinition}/segment/{nextSegmentation}/{nextSegmentDefinition}/<tail>", canonicalSegmentRequestHandler::get)
            get("/{objectId}/c/{segmentId}/segment/{segmentation}/{segmentDefinition}/segment/{nextSegmentation}/{nextSegmentDefinition}", canonicalSegmentRequestHandler::get)
            get("/{objectId}/c/{segmentId}/segment/{segmentation}/{segmentDefinition}", canonicalSegmentRequestHandler::get)
            get("/{objectId}/c/{segmentId}*", cachedSegmentRequestHandler::get)
            post("/add", addFileRequestHandler::post)
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