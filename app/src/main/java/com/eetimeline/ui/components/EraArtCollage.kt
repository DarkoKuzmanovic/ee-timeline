package com.eetimeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eetimeline.core.CurriculumUnit
import com.eetimeline.core.Episode

/**
 * Quiet mosaic of episode artwork, drawn in a [columns] x [rows] grid.
 *
 * Every tile sits on an era-tinted gradient, so tiles that have no URL, are still loading, or fail
 * to load (offline) read as a calm placeholder instead of a hole. Dimming and fading are left to
 * the caller so the same collage can sit behind both a list card and a full-bleed hero.
 */
@Composable
fun EraArtCollage(
    imageUrls: List<String>,
    eraColor: Color,
    eraIcon: ImageVector,
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(columns) { column ->
                    ArtTile(
                        url = imageUrls.getOrNull(row * columns + column),
                        eraColor = eraColor,
                        eraIcon = eraIcon,
                        strong = (row + column) % 2 == 0,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtTile(
    url: String?,
    eraColor: Color,
    eraIcon: ImageVector,
    strong: Boolean,
    modifier: Modifier = Modifier,
) {
    val top = if (strong) 0.30f else 0.22f
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(eraColor.copy(alpha = top), eraColor.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            // Visible while loading and when the fetch fails offline; covered by the artwork once decoded.
            Icon(
                imageVector = eraIcon,
                contentDescription = null,
                tint = eraColor.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxSize(0.38f),
            )
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context).data(thumbnailUrl(url)).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Distinct episode artwork URLs for [unit], sampled evenly across its episodes so the collage
 * reflects the whole era rather than only its first topic.
 */
fun unitArtworkUrls(unit: CurriculumUnit, episodes: Map<String, Episode>, count: Int): List<String> {
    val distinct = unit.topics
        .asSequence()
        .flatMap { topic -> topic.groups.asSequence().flatMap { it.episodeIds.asSequence() } }
        .mapNotNull { episodes[it]?.imageUrl?.takeIf(String::isNotBlank) }
        .distinct()
        .toList()
    if (distinct.size <= count || count < 2) return distinct.take(count)
    return List(count) { distinct[it * (distinct.size - 1) / (count - 1)] }
}

// The feed serves 3000px artwork through imgix, which honours max-w/max-h; ask for a tile-sized
// image so a screen of collages does not download dozens of multi-megabyte covers.
private fun thumbnailUrl(url: String): String =
    if ("megaphone.imgix.net" in url) {
        url.replace(Regex("max-w=\\d+"), "max-w=240").replace(Regex("max-h=\\d+"), "max-h=240")
    } else {
        url
    }
