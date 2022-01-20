package org.megras.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Config(
    val objectStoreBase: String = "store",
    val httpPort: Int = 8080
) {
    companion object{
        fun read(file: File): Config? {
            return try{
                Json.decodeFromString(serializer(), file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }
}
