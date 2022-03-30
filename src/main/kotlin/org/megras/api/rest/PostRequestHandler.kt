package org.megras.api.rest

import io.javalin.http.Context

interface PostRequestHandler {

    fun post(ctx: Context)

}