package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkTextSecondary

@Composable
fun AlbumArtwork(
    artworkUri: String?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(DarkSurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover for $title",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Minimalist geometric audio record placeholder
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Outer edge ring
                drawCircle(
                    color = DarkBorderSubtle,
                    radius = maxRadius * 0.9f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // Concentric precision audio grooves
                drawCircle(
                    color = DarkBorderSubtle.copy(alpha = 0.6f),
                    radius = maxRadius * 0.65f,
                    center = center,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = DarkBorderSubtle.copy(alpha = 0.4f),
                    radius = maxRadius * 0.42f,
                    center = center,
                    style = Stroke(width = 1f)
                )

                // Center spindle core
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = maxRadius * 0.18f,
                    center = center
                )
            }

            // Discreet initial letter
            val initial = title.trim().take(1).uppercase()
            if (initial.isNotBlank()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = DarkTextSecondary
                )
            }
        }
    }
}
