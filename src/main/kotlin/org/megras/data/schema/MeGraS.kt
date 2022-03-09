package org.megras.data.schema

enum class MeGraS(private val suffix: String) {


    RAW_ID("rawId")


    ;

    val string: String
    get() = "${prefix}${suffix}"

    companion object {
        private val prefix = "http://megras.org/schema#"
    }

}