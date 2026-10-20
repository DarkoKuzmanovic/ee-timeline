package com.eetimeline.core

import kotlinx.serialization.Serializable

@Serializable
data class Curriculum(
    val source: String = "",
    val feedUrl: String = "",
    val generatedAt: String = "",
    val units: List<CurriculumUnit> = emptyList(),
    val episodes: Map<String, Episode> = emptyMap(),
    val issues: List<String> = emptyList(),
)

@Serializable
data class CurriculumUnit(
    val number: Int,
    val title: String,
    val period: String,
    val startYear: Int,
    val endYear: Int,
    val summary: String,
    val topics: List<Topic>,
)

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val detail: String?,
    val groups: List<EpisodeGroup>,
)

@Serializable
data class EpisodeGroup(
    val title: String?,
    val episodeIds: List<String>,
)

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val pageUrl: String,
    val audioUrl: String,
    val pubDate: String,
    val durationSec: Int,
    val description: String,
    val imageUrl: String?,
    val approxYear: Int,
    val yearLabel: String,
)
