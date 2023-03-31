package org.megras.api.rest.handlers

import io.javalin.http.ContentType
import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.graph.StringValue
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.graphstore.QuadSet
import org.megras.id.ObjectId

class AboutObjectRequestHandler(private val quads: QuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

    override fun get(ctx: Context) {

        val objectId = ObjectId(ctx.pathParam("objectId"))

        val relevant = quads.filter(setOf(objectId), null,null) + quads.filter(null, null, setOf(objectId))

        if (quads.isEmpty()) {
            throw RestErrorStatus.notFound
        }

        val buf = StringBuilder()

        buf.append(
          """
              <!DOCTYPE html>
              <html><head><title>About '$objectId'</title></head>
              <body>
              
          """.trimIndent()
        )

        val type = quads.filterPredicate(MeGraS.MEDIA_TYPE.uri).firstOrNull()?.`object` as? StringValue

        when(type?.value) {
            MediaType.IMAGE.name -> {
                buf.append("<img src='${objectId.toPath()}' />")
            }

            else -> {/* no preview */}
        }

        buf.append("\n<br><textarea readonly style='width: 100%; min-height: 200px; resize: vertical;'>\n")
        relevant.forEach {
            buf.append("${it.subject} ${it.predicate} ${it.`object`}\n")
        }

        buf.append("""
            </textarea>
            </body>
            </html>
        """.trimIndent())

        ctx.contentType(ContentType.TEXT_HTML.mimeType)
        ctx.result(buf.toString())
    }
}