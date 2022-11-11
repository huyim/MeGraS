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
    TIFF("image/tiff"),

    //audio
    AAC("audio/x-aac"),
    ADP("audio/adpcm"),
    AIF("audio/x-aiff"),
    AU("audio/basic"),
    MPEG_A("audio/mpeg"),
    MIDI("audio/midi"),
    MP4_A("audio/mp4"),
    OGG("audio/ogg"),
    WAV("audio/x-wav"),
    WAX("audio/x-ms-wax"),
    WMA("audio/x-ms-wma");

    companion object {
        val extensionMap = mapOf(
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
            "tiff" to TIFF,

            "aac" to AAC,
            "adp" to ADP,
            "aif" to AIF,
            "aifc" to AIF,
            "aiff" to AIF,
            "au" to AU,
            "snd" to AU,
            "m2a" to MPEG_A,
            "m3a" to MPEG_A,
            "mp2" to MPEG_A,
            "mp2a" to MPEG_A,
            "mp3" to MPEG_A,
            "mpga" to MPEG_A,
            "kar" to MIDI,
            "mid" to MIDI,
            "midi" to MIDI,
            "rmi" to MIDI,
            "mp4a" to MP4_A,
            "oga" to OGG,
            "ogg" to OGG,
            "spx" to OGG,
            "wav" to WAV,
            "wax" to WAX,
            "wma" to WMA

        )

        fun mimeType(fileExtension: String) = extensionMap[fileExtension.lowercase()] ?: OCTET_STREAM

    }





}