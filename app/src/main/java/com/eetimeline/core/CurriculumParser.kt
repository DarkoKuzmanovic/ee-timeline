package com.eetimeline.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object CurriculumParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses an asset's contents. Missing, blank, or malformed content yields an empty curriculum. */
    fun parse(content: String?): Curriculum {
        if (content.isNullOrBlank()) return Curriculum()
        return try {
            json.decodeFromString<Curriculum>(content)
        } catch (_: SerializationException) {
            Curriculum()
        }
    }
}
