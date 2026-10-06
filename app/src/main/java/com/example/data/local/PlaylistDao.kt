package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.Collaborator
import com.example.data.model.Playlist
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistById(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistByIdSync(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylistDetails(playlistId: Long, name: String, description: String)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_tracks WHERE id = :trackRefId")
    suspend fun removeTrackRefById(trackRefId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("SELECT COUNT(*) > 0 FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistTracksSync(playlistId: Long): List<PlaylistTrackCrossRef>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_tracks pt ON s.id = pt.songId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.orderIndex ASC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_tracks pt ON s.id = pt.songId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.orderIndex ASC
    """)
    suspend fun getSongsForPlaylistSync(playlistId: Long): List<Song>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>

    @Query("""
        SELECT SUM(s.durationMs) FROM songs s
        INNER JOIN playlist_tracks pt ON s.id = pt.songId
        WHERE pt.playlistId = :playlistId
    """)
    fun getPlaylistTotalDurationMs(playlistId: Long): Flow<Long?>

    @Transaction
    suspend fun reorderPlaylistTracks(playlistId: Long, newTrackOrder: List<Long>) {
        val existing = getPlaylistTracksSync(playlistId)
        val trackMap = existing.associateBy { it.songId }
        clearPlaylistTracks(playlistId)
        newTrackOrder.forEachIndexed { index, songId ->
            val prev = trackMap[songId]
            if (prev != null) {
                addTrackToPlaylist(prev.copy(id = 0, orderIndex = index))
            }
        }
    }

    @Query("SELECT * FROM collaborators WHERE playlistId = :playlistId")
    fun getCollaborators(playlistId: Long): Flow<List<Collaborator>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollaborator(collaborator: Collaborator): Long

    @Query("DELETE FROM collaborators WHERE id = :id")
    suspend fun deleteCollaborator(id: Long)
}
