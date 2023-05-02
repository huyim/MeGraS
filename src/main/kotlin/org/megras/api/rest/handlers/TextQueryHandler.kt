package org.megras.api.rest.handlers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*
import org.megras.api.rest.PostRequestHandler
import org.megras.api.rest.RestErrorStatus
import org.megras.api.rest.data.ApiQuad
import org.megras.api.rest.data.ApiTextQuery
import org.megras.api.rest.data.ApiTextQueryResult
import org.megras.data.graph.QuadValue
import org.megras.graphstore.QuadSet


class TextQueryHandler(private val quads: QuadSet) : PostRequestHandler {

    @OpenApi(
        summary = "Queries the Graph.",
        path = "/textquery",
        tags = ["TextQuery"],
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(ApiTextQuery::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(ApiTextQueryResult::class)]),
            OpenApiResponse("400", [OpenApiContent(RestErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(RestErrorStatus::class)]),
        ]
    )
    override fun post(ctx: Context) {

        val query = try {
            ctx.bodyAsClass(ApiTextQuery::class.java)
        } catch (e: BadRequestResponse) {
            throw RestErrorStatus(400, "invalid query")
        }

        if (query.filterText == null) {
            throw RestErrorStatus(400, "invalid query")
        }

        val results = quads.textFilter(
            query.filterText//.mapNotNull { if (it != null) String(it) else null },
        ).map { ApiQuad(it) }

        ctx.json(ApiTextQueryResult(results))
    }
}