package com.eetimeline.data

import android.content.Context
import com.eetimeline.core.Curriculum
import com.eetimeline.core.CurriculumParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CurriculumRepository(private val context: Context) {
    suspend fun load(): Curriculum = withContext(Dispatchers.IO) {
        val content = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()
        CurriculumParser.parse(content)
    }

    private companion object {
        const val ASSET_NAME = "curriculum.json"
    }
}
