package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long = 0,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val albumArtist: String = "",
    val durationMs: Long = 0,
    val path: String = "",
    val contentUriString: String = "",
    val albumArtUri: String? = null,
    val genre: String = "Pop",
    val year: Int = 2024,
    val trackNumber: Int = 1,
    val discNumber: Int = 1,
    val sizeBytes: Long = 0,
    val mimeType: String = "audio/mpeg",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val customTags: String = "",
    val isMetadataEdited: Boolean = false
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val tagsList: List<String>
        get() = if (customTags.isBlank()) emptyList() else customTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
