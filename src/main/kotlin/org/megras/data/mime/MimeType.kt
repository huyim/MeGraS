package org.megras.data.mime

enum class MimeType(val mimeString: String) {

    //binary
    OCTET_STREAM("octet-stream"),

    //text
    CSS("text/css"),
    CSV("text/csv"),
    HTML("text/html"),
    JS("application/javascript"),
    JSON("application/json"),
    TEXT("text/plain"),

    //images
    BMP("image/bmp"),
    GIF("image/gif"),
    JPEG_I("image/jpeg"),
    PNG("image/png"),
    SVG("image/svg+xml"),
    TIFF("image/tiff");


    companion object {
        private val extensionMap = mapOf(
            "css" to CSS,
            "csv" to CSV,
            "htm" to HTML,
            "html" to HTML,
            "js" to JS,
            "json" to JSON,
            "log" to TEXT,
            "text" to TEXT,
            "txt" to TEXT,

            "bmp" to BMP,
            "gif" to GIF,
            "jpe" to JPEG_I,
            "jpeg" to JPEG_I,
            "jpg" to JPEG_I,
            "png" to PNG,
            "svg" to SVG,
            "svgz" to SVG,
            "tif" to TIFF,
            "tiff" to TIFF
        )

        fun mimeType(fileExtension: String) = extensionMap[fileExtension.lowercase()] ?: OCTET_STREAM

    }




}