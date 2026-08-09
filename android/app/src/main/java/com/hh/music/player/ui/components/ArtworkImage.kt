package com.hh.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * Cover/artwork loader with a unified music-note placeholder: used on load, on
 * error, and for every non-http url (local files, missing covers). Keeps the
 * visuals consistent across lists, tiles and the player screen.
 */
@Composable
fun ArtworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote
) {
    if (url.startsWith("http")) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = { Placeholder(placeholderColor, placeholderIcon) },
            error = { Placeholder(placeholderColor, placeholderIcon) }
        )
    } else {
        Box(modifier.background(placeholderColor), contentAlignment = Alignment.Center) {
            Icon(
                placeholderIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Placeholder(color: Color, icon: ImageVector) {
    Box(Modifier.background(color), contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}