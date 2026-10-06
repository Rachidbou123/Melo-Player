package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.AccentActive
import com.example.ui.theme.AccentDestructive

@Composable
fun SongItemRow(
    song: Song,
    isPlayingThisSong: Boolean,
    onSongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditMetadata: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    trackIndex: Int? = null,
    isCompact: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick)
            .padding(horizontal = 16.dp, vertical = if (isCompact) 6.dp else 9.dp)
            .testTag("song_row_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (trackIndex != null) {
            Text(
                text = "$trackIndex",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = if (isPlayingThisSong) AccentActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
        }

        // Album Artwork Thumbnail
        Box(
            modifier = Modifier.size(if (isCompact) 36.dp else 44.dp),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtwork(
                artworkUri = song.albumArtUri,
                title = song.title,
                modifier = Modifier.size(if (isCompact) 36.dp else 44.dp),
                cornerRadius = 4.dp
            )

            if (isPlayingThisSong) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    MinimalEqualizerBars()
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isPlayingThisSong) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isCompact) 13.sp else 14.5.sp
                ),
                color = if (isPlayingThisSong) AccentActive else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (song.artist.isNotBlank()) "${song.artist} • ${song.durationFormatted}" else song.durationFormatted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Favorite Toggle
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(44.dp).testTag("fav_btn_${song.id}")
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Remove favorite" else "Favorite",
                tint = if (song.isFavorite) AccentDestructive else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }

        // More options dropdown
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(44.dp).testTag("options_btn_${song.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Play Next", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Add to Queue", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Add to Playlist", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Start Radio", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onStartRadio?.invoke()
                    }
                )
                if (onGoToAlbum != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Go to Album", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuExpanded = false
                            onGoToAlbum()
                        }
                    )
                }
                if (onGoToArtist != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Go to Artist", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuExpanded = false
                            onGoToArtist()
                        }
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Edit Metadata", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onEditMetadata()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
            }
        }
    }
}

@Composable
private fun MinimalEqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val h1 by transition.animateFloat(
        initialValue = 3f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(320), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 12f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(280), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.5.dp).height(h1.dp).background(AccentActive, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.5.dp).height(h2.dp).background(AccentActive, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.5.dp).height(h3.dp).background(AccentActive, RoundedCornerShape(1.dp)))
    }
}
