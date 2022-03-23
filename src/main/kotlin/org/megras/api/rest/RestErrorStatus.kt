package org.megras.api.rest

class RestErrorStatus(val statusCode: Int, override val message: String): Exception() {

    companion object {
        val notFound = RestErrorStatus(404, "Not found.")
    }

}