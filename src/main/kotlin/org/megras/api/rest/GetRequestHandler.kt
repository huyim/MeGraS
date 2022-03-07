package org.megras.api.rest

import io.javalin.http.Context

interface GetRequestHandler {

    fun get(ctx: Context)

}