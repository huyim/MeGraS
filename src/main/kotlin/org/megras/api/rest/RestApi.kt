package org.megras.api.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import org.megras.api.rest.handlers.*
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.ObjectStoreResult
import org.megras.data.model.Config
import org.megras.graphstore.MutableQuadSet

object RestApi {

    private var javalin: Javalin? = null
    private var canonicalObjectRequestHandler: CanonicalObjectRequestHandler? = null
    private var cachedSegmentRequestHandler: CachedSegmentRequestHandler? = null
    private var canonicalSegmentRequestHandler: CanonicalSegmentRequestHandler? = null

    fun init(config: Config, objectStore: FileSystemObjectStore, quadSet: MutableQuadSet) {

        if (javalin != null) {
            stop() //stop instance in case there already is one. should not happen, just in case
        }

        val rawObjectRequestHandler = RawObjectRequestHandler(objectStore)
        val aboutObjectRequestHandler = AboutObjectRequestHandler(quadSet, objectStore)
        val addFileRequestHandler = AddFileRequestHandler(quadSet, objectStore)
        canonicalObjectRequestHandler = CanonicalObjectRequestHandler(quadSet, objectStore)
        cachedSegmentRequestHandler = CachedSegmentRequestHandler(quadSet, objectStore)
        canonicalSegmentRequestHandler = CanonicalSegmentRequestHandler(quadSet, objectStore)

        javalin = Javalin.create {
            it.plugins.enableCors { cors ->
                cors.add { corsPluginConfig ->
                    corsPluginConfig.anyHost()
                }
            }
            it.showJavalinBanner = false
        }.routes {
            post("/add", addFileRequestHandler::post)
            get("/raw/{objectId}", rawObjectRequestHandler::get)
            get("/<objectId>/about", aboutObjectRequestHandler::get)
            get("/<path>", this::getRequestHandler)
        }.exception(RestErrorStatus::class.java) { e, ctx ->
            ctx.status(e.statusCode)
            ctx.result(e.message)
        }.start(config.httpPort)
    }

    private fun getRequestHandler(ctx: Context) {
        val path = ctx.pathParam("path")
        val pathParts = path.split("/")
        val lookInCache = ctx.queryParam("nocache") == null

        var objectId: String? = null
        var result: ObjectStoreResult? = null

        var current = 0

        try {
            while (current < pathParts.size) {

                // /segment/<type>/<definition>
                if (pathParts[current] == "segment" && result != null && objectId != null) {
                    val segmentationType = pathParts[current + 1]
                    val segmentationDefinition = pathParts[current + 2]

                    // first look if it exists in cache, otherwise compute it
                    val cachedObjectId = canonicalSegmentRequestHandler?.findInQuad("$objectId/segment/$segmentationType/$segmentationDefinition")
                    if (lookInCache && cachedObjectId != null) {
                        result = cachedSegmentRequestHandler?.get(cachedObjectId)
                    } else {
                        result = canonicalSegmentRequestHandler?.get(objectId, result, segmentationType, segmentationDefinition)
                    }
                    current += 3
                }
                // /<documentId>/c/<segmentId>
                else if (pathParts.size > current + 1 && pathParts[current + 1] == "c") {
                    objectId = pathParts.subList(current, current + 3).joinToString("/")
                    result = cachedSegmentRequestHandler?.get(objectId)
                    current += 3
                }
                // /<documentId>
                else {
                    objectId = pathParts[current]
                    result = canonicalObjectRequestHandler?.get(objectId)
                    current += 1
                }
            }

            if (result != null) {
                ctx.writeSeekableStream(result.inputStream(), result.descriptor.mimeType.mimeString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            when (e) {
                is RestErrorStatus -> throw e
                else -> throw RestErrorStatus(403, "Invalid segmentation")
            }
        }
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}