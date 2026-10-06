package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.MediaStoreScanner
import com.example.data.model.Collaborator
import com.example.data.model.PlayHistory
import com.example.data.model.Playlist
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(val database: AppDatabase) {

    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val historyDao = database.historyDao()

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val recentlyAdded: Flow<List<Song>> = songDao.getRecentlyAdded(50)
    val recentlyPlayed: Flow<List<Song>> = songDao.getRecentlyPlayed(50)
    val mostPlayed: Flow<List<Song>> = songDao.getMostPlayed(50)
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val allArtists: Flow<List<String>> = songDao.getAllArtists()
    val allAlbums: Flow<List<String>> = songDao.getAllAlbums()
    val allGenres: Flow<List<String>> = songDao.getAllGenres()

    suspend fun initializeLibrary(context: Context): Int = withContext(Dispatchers.IO) {
        scanDeviceAudio(context)
    }

    suspend fun scanDeviceAudio(context: Context): Int = withContext(Dispatchers.IO) {
        val scanned = MediaStoreScanner.scanDeviceAudio(context)
        var newSongsCount = 0
        for (scannedSong in scanned) {
            val existing = songDao.getSongByPath(scannedSong.path)
            if (existing == null) {
                songDao.insertSong(scannedSong)
                newSongsCount++
            } else if (!existing.isMetadataEdited) {
                songDao.updateSong(
                    existing.copy(
                        mediaStoreId = scannedSong.mediaStoreId,
                        contentUriString = scannedSong.contentUriString,
                        albumArtUri = scannedSong.albumArtUri ?: existing.albumArtUri,
                        durationMs = if (scannedSong.durationMs > 0) scannedSong.durationMs else existing.durationMs,
                        sizeBytes = scannedSong.sizeBytes
                    )
                )
            }
        }
        newSongsCount
    }

    suspend fun toggleFavorite(songId: Long, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        songDao.setFavorite(songId, !currentFavorite)
    }

    suspend fun recordPlay(songId: Long) = withContext(Dispatchers.IO) {
        songDao.incrementPlayCount(songId, System.currentTimeMillis())
        historyDao.recordPlay(PlayHistory(songId = songId, playedAt = System.currentTimeMillis()))
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song.copy(isMetadataEdited = true))
    }

    suspend fun getSongById(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getSongByIdSync(id)
    }

    suspend fun getAllSongsSync(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongsSync()
    }

    fun searchSongs(query: String): Flow<List<Song>> = songDao.searchSongs(query)

    fun getSongsByArtist(artist: String): Flow<List<Song>> = songDao.getSongsByArtist(artist)
    fun getSongsByAlbum(album: String): Flow<List<Song>> = songDao.getSongsByAlbum(album)
    fun getSongsByGenre(genre: String): Flow<List<Song>> = songDao.getSongsByGenre(genre)

    // Playlists
    suspend fun createPlaylist(
        name: String,
        description: String = "",
        isDynamic: Boolean = false,
        rulesJson: String = "",
        isCollaborative: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(
            Playlist(
                name = name,
                description = description,
                isDynamic = isDynamic,
                rulesJson = rulesJson,
                isCollaborative = isCollaborative,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val existingSongs = playlistDao.getSongsForPlaylistSync(playlistId)
        playlistDao.addTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                songId = songId,
                orderIndex = existingSongs.size
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> = playlistDao.getSongsForPlaylist(playlistId)

    suspend fun getSongsForPlaylistSync(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        playlistDao.getSongsForPlaylistSync(playlistId)
    }

    fun getCollaborators(playlistId: Long): Flow<List<Collaborator>> = playlistDao.getCollaborators(playlistId)

    suspend fun addCollaborator(playlistId: Long, name: String, email: String, role: String) = withContext(Dispatchers.IO) {
        playlistDao.insertCollaborator(
            Collaborator(
                playlistId = playlistId,
                name = name,
                email = email,
                role = role
            )
        )
    }

    suspend fun evaluateDynamicPlaylist(rulesJson: String): List<Song> = withContext(Dispatchers.IO) {
        val all = songDao.getAllSongsSync()
        if (rulesJson.isBlank()) return@withContext all

        when {
            rulesJson.startsWith("GENRE:") -> {
                val genre = rulesJson.removePrefix("GENRE:").trim()
                all.filter { it.genre.contains(genre, ignoreCase = true) }
            }
            rulesJson.startsWith("ARTIST:") -> {
                val artist = rulesJson.removePrefix("ARTIST:").trim()
                all.filter { it.artist.contains(artist, ignoreCase = true) }
            }
            rulesJson == "FAVORITES_UNDER_4MIN" -> {
                all.filter { it.isFavorite && it.durationMs in 1..240000L }
            }
            rulesJson == "HIGH_PLAY_COUNT" -> {
                all.filter { it.playCount >= 3 }
            }
            rulesJson == "RECENT_YEARS" -> {
                all.filter { it.year >= 2020 }
            }
            rulesJson == "FAVORITES" -> {
                all.filter { it.isFavorite }
            }
            else -> all
        }
    }

    suspend fun resetDatabase() = withContext(Dispatchers.IO) {
        songDao.deleteAllSongs()
        historyDao.clearHistory()
    }
}
