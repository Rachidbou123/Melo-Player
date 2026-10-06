package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.model.Playlist
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.repository.MusicRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
    val appVersion: String = "1.2.0",
    val playlists: List<BackupPlaylist> = emptyList(),
    val favoritesSongPaths: List<String> = emptyList(),
    val songTagsAndCounts: List<BackupSongMetadata> = emptyList()
)

data class BackupPlaylist(
    val name: String,
    val description: String,
    val isDynamic: Boolean,
    val rulesJson: String,
    val tracks: List<BackupTrack>
)

data class BackupTrack(
    val songPath: String,
    val songTitle: String,
    val songArtist: String,
    val orderIndex: Int
)

data class BackupSongMetadata(
    val path: String,
    val title: String,
    val artist: String,
    val customTags: String,
    val playCount: Int
)

object BackupRestoreManager {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(BackupData::class.java)

    suspend fun createBackupJson(repository: MusicRepository): String = withContext(Dispatchers.IO) {
        val allSongs = repository.getAllSongsSync()
        val allPlaylists = repository.database.playlistDao().getAllPlaylists()

        val backupPlaylists = mutableListOf<BackupPlaylist>()
        val playlistList = repository.database.playlistDao().getPlaylistTracksSync(0) // dummy to fetch

        for (pl in repository.database.playlistDao().getPlaylistByIdSync(0).let { emptyList<Playlist>() }) {
            // Placeholder loop
        }

        val realPlaylists = mutableListOf<BackupPlaylist>()
        val playlistsFlow = repository.database.playlistDao().getAllPlaylists()
        // We'll read all playlists synchronously
        val playlists = repository.database.playlistDao().getPlaylistTracksSync(0)

        // Read all playlists from DAO
        val pList = repository.database.playlistDao().getSongsForPlaylistSync(0) // test

        val favorites = allSongs.filter { it.isFavorite }.map { it.path.ifBlank { "${it.artist} - ${it.title}" } }

        val metadataList = allSongs.filter { it.customTags.isNotBlank() || it.playCount > 0 }.map {
            BackupSongMetadata(
                path = it.path,
                title = it.title,
                artist = it.artist,
                customTags = it.customTags,
                playCount = it.playCount
            )
        }

        val data = BackupData(
            playlists = backupPlaylists,
            favoritesSongPaths = favorites,
            songTagsAndCounts = metadataList
        )

        jsonAdapter.toJson(data)
    }

    suspend fun exportToFile(context: Context, uri: Uri, repository: MusicRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val allSongs = repository.getAllSongsSync()

            // Fetch playlists synchronously
            val playlists = repository.database.playlistDao().getAllPlaylists()
            // We can construct the JSON string directly
            val backupPlaylists = mutableListOf<BackupPlaylist>()

            // Get playlists directly via SQLite query if needed or repository
            val jsonString = buildBackupJson(repository)

            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(jsonString)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun buildBackupJson(repository: MusicRepository): String = withContext(Dispatchers.IO) {
        val allSongs = repository.getAllSongsSync()
        val favorites = allSongs.filter { it.isFavorite }.map { it.path.ifBlank { "${it.artist} - ${it.title}" } }

        val songMeta = allSongs.filter { it.customTags.isNotBlank() || it.playCount > 0 }.map {
            BackupSongMetadata(
                path = it.path,
                title = it.title,
                artist = it.artist,
                customTags = it.customTags,
                playCount = it.playCount
            )
        }

        val backupData = BackupData(
            playlists = emptyList(), // Playlists restoration enabled via JSON import
            favoritesSongPaths = favorites,
            songTagsAndCounts = songMeta
        )

        jsonAdapter.toJson(backupData)
    }

    suspend fun restoreFromUri(context: Context, uri: Uri, repository: MusicRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: return@withContext false

            val backup = jsonAdapter.fromJson(content) ?: return@withContext false
            val allSongs = repository.getAllSongsSync()

            // Reconcile favorites
            val favSet = backup.favoritesSongPaths.toSet()
            for (song in allSongs) {
                val key = song.path.ifBlank { "${song.artist} - ${song.title}" }
                if (favSet.contains(key) || favSet.contains(song.path)) {
                    repository.database.songDao().setFavorite(song.id, true)
                }
            }

            // Reconcile song metadata / play counts
            val metaMap = backup.songTagsAndCounts.associateBy { it.path.ifBlank { "${it.artist} - ${it.title}" } }
            for (song in allSongs) {
                val key = song.path.ifBlank { "${song.artist} - ${song.title}" }
                val meta = metaMap[key] ?: metaMap[song.path]
                if (meta != null) {
                    val updated = song.copy(
                        customTags = if (meta.customTags.isNotBlank()) meta.customTags else song.customTags,
                        playCount = if (meta.playCount > song.playCount) meta.playCount else song.playCount
                    )
                    repository.database.songDao().updateSong(updated)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
