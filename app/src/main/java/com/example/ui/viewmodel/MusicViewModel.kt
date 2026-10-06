package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiPlaylistResult
import com.example.ai.LocalRecommendationGroup
import com.example.ai.MusicAiAdvisor
import com.example.audio.PlaybackState
import com.example.audio.PlaybackStateManager
import com.example.audio.VisualizerMode
import com.example.data.local.AppDatabase
import com.example.data.local.BackupRestoreManager
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.voice.VoiceCommand
import com.example.voice.VoiceCommandManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavTab {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLISTS,
    SETTINGS
}

enum class LibrarySortOption(val displayName: String) {
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_PLAYED("Recently Played"),
    MOST_PLAYED("Most Played"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    YEAR("Release Year")
}

enum class LibraryViewMode {
    LIST,
    COMPACT,
    GRID
}

enum class LibraryTab(val displayName: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists"),
    DOWNLOADS("Downloads")
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = MusicRepository(database)
    val playbackManager = PlaybackStateManager(application, repository, viewModelScope)
    val voiceManager = VoiceCommandManager(application)

    // Playback state exposed to UI
    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState

    // Navigation and Sheets UI state
    val currentTab = MutableStateFlow(NavTab.HOME)
    val isPlayerExpanded = MutableStateFlow(false)
    val isQueueSheetOpen = MutableStateFlow(false)
    val isMetadataEditorOpen = MutableStateFlow(false)
    val songToEdit = MutableStateFlow<Song?>(null)
    val addToPlaylistSheetSong = MutableStateFlow<Song?>(null)
    val isVoiceOverlayOpen = MutableStateFlow(false)
    val isScanningStorage = MutableStateFlow(false)
    val scanStatusMessage = MutableStateFlow("")
    val isShareSheetOpen = MutableStateFlow(false)
    val shareItem = MutableStateFlow<Song?>(null)
    val isEqualizerOpen = MutableStateFlow(false)
    val isDarkTheme = MutableStateFlow(true)

    // AI & Recommendations state
    val aiPlaylistResult = MutableStateFlow<AiPlaylistResult?>(null)
    val localRecommendations = MutableStateFlow<List<LocalRecommendationGroup>>(emptyList())

    // Library State
    val libraryTab = MutableStateFlow(LibraryTab.SONGS)
    val libraryViewMode = MutableStateFlow(LibraryViewMode.LIST)
    val librarySortOption = MutableStateFlow(LibrarySortOption.RECENTLY_ADDED)
    val librarySortAscending = MutableStateFlow(true)
    val activeGenreFilter = MutableStateFlow<String?>(null)
    val activeArtistFilter = MutableStateFlow<String?>(null)

    // Search State
    val searchQuery = MutableStateFlow("")

    // Raw Repository Flows
    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<Song>> = repository.recentlyAdded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Song>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArtists: StateFlow<List<String>> = repository.allArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAlbums: StateFlow<List<String>> = repository.allAlbums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGenres: StateFlow<List<String>> = repository.allGenres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered & Sorted Library Songs Flow
    val filteredSongs: StateFlow<List<Song>> = combine(
        allSongs,
        activeGenreFilter,
        activeArtistFilter,
        librarySortOption,
        librarySortAscending
    ) { songs, genreFilter, artistFilter, sort, asc ->
        var result = songs
        if (genreFilter != null) {
            result = result.filter { it.genre.equals(genreFilter, ignoreCase = true) }
        }
        if (artistFilter != null) {
            result = result.filter { it.artist.equals(artistFilter, ignoreCase = true) }
        }

        val sorted = when (sort) {
            LibrarySortOption.TITLE -> result.sortedBy { it.title.lowercase() }
            LibrarySortOption.ARTIST -> result.sortedBy { it.artist.lowercase() }
            LibrarySortOption.ALBUM -> result.sortedBy { it.album.lowercase() }
            LibrarySortOption.DURATION -> result.sortedBy { it.durationMs }
            LibrarySortOption.YEAR -> result.sortedBy { it.year }
            LibrarySortOption.MOST_PLAYED -> result.sortedBy { it.playCount }
            LibrarySortOption.RECENTLY_PLAYED -> result.sortedBy { it.lastPlayedAt }
            LibrarySortOption.RECENTLY_ADDED -> result.sortedBy { it.dateAdded }
        }
        if (asc) sorted else sorted.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Results Flow
    val searchResults: StateFlow<List<Song>> = combine(
        allSongs,
        searchQuery
    ) { songs, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.genre.lowercase().contains(q) ||
                it.customTags.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanDeviceStorage()
        setupVoiceListener()

        viewModelScope.launch {
            combine(allSongs, recentlyPlayed, mostPlayed, favoriteSongs) { songs, recent, most, favs ->
                MusicAiAdvisor.generateLocalRecommendations(songs, recent, most, favs)
            }.collect { recs ->
                localRecommendations.value = recs
            }
        }
    }

    fun scanDeviceStorage() {
        viewModelScope.launch {
            isScanningStorage.value = true
            scanStatusMessage.value = "Scanning local audio files..."
            val newCount = repository.scanDeviceAudio(getApplication())
            isScanningStorage.value = false
            scanStatusMessage.value = if (newCount > 0) "Imported $newCount new songs" else "Library is up to date"
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        playbackManager.playSong(song, queue)
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun playNext() {
        playbackManager.playNext()
    }

    fun playPrevious() {
        playbackManager.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeat()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setSpeed(speed)
    }

    fun setVolume(volume: Float) {
        playbackManager.setVolume(volume)
    }

    fun setCrossfadeSeconds(seconds: Int) {
        playbackManager.setCrossfadeSeconds(seconds)
    }

    fun setSleepTimer(minutes: Int) {
        playbackManager.setSleepTimer(minutes)
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        playbackManager.setVisualizerMode(mode)
    }

    fun setEqualizerPreset(preset: String) {
        playbackManager.setEqualizerPreset(preset)
    }

    fun toggleBassBoost() {
        playbackManager.toggleBassBoost()
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
        Toast.makeText(getApplication(), "Added to Queue: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    fun playNextInQueue(song: Song) {
        playbackManager.playNextInQueue(song)
        Toast.makeText(getApplication(), "Playing Next: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    fun startRadioForSong(song: Song) {
        viewModelScope.launch {
            val songs = allSongs.value
            val radioQueue = songs.filter {
                it.artist.equals(song.artist, ignoreCase = true) ||
                it.genre.equals(song.genre, ignoreCase = true)
            }.shuffled()

            val effective = if (radioQueue.isNotEmpty()) radioQueue else songs.shuffled()
            if (effective.isNotEmpty()) {
                playSong(song, effective)
                Toast.makeText(getApplication(), "Started Radio based on ${song.artist}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Playlists
    suspend fun createPlaylist(
        name: String,
        description: String = "",
        isDynamic: Boolean = false,
        rulesJson: String = ""
    ): Long {
        return repository.createPlaylist(name, description, isDynamic, rulesJson)
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String = "") {
        viewModelScope.launch {
            repository.database.playlistDao().updatePlaylistDetails(playlistId, name, description)
            Toast.makeText(getApplication(), "Playlist updated", Toast.LENGTH_SHORT).show()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            Toast.makeText(getApplication(), "Deleted playlist ${playlist.name}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        repository.addSongToPlaylist(playlistId, songId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        repository.removeSongFromPlaylist(playlistId, songId)
    }

    fun reorderPlaylistTracks(playlistId: Long, songIdsInOrder: List<Long>) {
        viewModelScope.launch {
            repository.database.playlistDao().reorderPlaylistTracks(playlistId, songIdsInOrder)
        }
    }

    // AI & Natural Language Playlist Generation
    fun processAiPlaylistRequest(prompt: String, relaxLevel: Int = 0) {
        viewModelScope.launch {
            val library = allSongs.value
            val result = MusicAiAdvisor.processNaturalLanguageRequest(prompt, library, relaxLevel)
            aiPlaylistResult.value = result
        }
    }

    fun saveAiPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch {
            if (songs.isNotEmpty()) {
                val playlistId = repository.createPlaylist(name, "Generated via AI Assistant")
                songs.forEach { song ->
                    repository.addSongToPlaylist(playlistId, song.id)
                }
                aiPlaylistResult.value = null
                Toast.makeText(getApplication(), "Saved playlist: $name (${songs.size} tracks)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Metadata Editing
    fun updateSongMetadata(updatedSong: Song) {
        viewModelScope.launch {
            repository.updateSong(updatedSong)
            isMetadataEditorOpen.value = false
            songToEdit.value = null
            Toast.makeText(getApplication(), "Metadata saved for ${updatedSong.title}", Toast.LENGTH_SHORT).show()
        }
    }

    // Backup & Restore
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val success = BackupRestoreManager.exportToFile(getApplication(), uri, repository)
            if (success) {
                Toast.makeText(getApplication(), "Backup exported successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(getApplication(), "Failed to export backup", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val success = BackupRestoreManager.restoreFromUri(getApplication(), uri, repository)
            if (success) {
                Toast.makeText(getApplication(), "Backup restored successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(getApplication(), "Failed to restore backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Voice Overlay
    fun openVoiceAssistant() {
        isVoiceOverlayOpen.value = true
        voiceManager.startListening(
            onCommandRecognized = { command ->
                isVoiceOverlayOpen.value = false
                handleVoiceCommand(command)
            },
            onError = { err ->
                isVoiceOverlayOpen.value = false
                Toast.makeText(getApplication(), "Voice error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun cancelVoiceCommand() {
        voiceManager.stopListening()
        isVoiceOverlayOpen.value = false
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.Play -> togglePlayPause()
            VoiceCommand.Pause -> togglePlayPause()
            VoiceCommand.Next -> playNext()
            VoiceCommand.Previous -> playPrevious()
            VoiceCommand.ShuffleAll -> toggleShuffle()
            VoiceCommand.ShuffleFavorites -> {
                val favs = favoriteSongs.value
                if (favs.isNotEmpty()) playSong(favs.random(), favs.shuffled())
            }
            VoiceCommand.VolumeUp -> setVolume(1.0f)
            VoiceCommand.VolumeDown -> setVolume(0.5f)
            is VoiceCommand.PlayArtist -> {
                val matches = allSongs.value.filter { it.artist.contains(command.artist, ignoreCase = true) }
                if (matches.isNotEmpty()) playSong(matches.first(), matches)
            }
            is VoiceCommand.PlaySong -> {
                val matches = allSongs.value.filter { it.title.contains(command.title, ignoreCase = true) }
                if (matches.isNotEmpty()) playSong(matches.first(), matches)
            }
            is VoiceCommand.PlayPlaylist -> {
                val matches = allPlaylists.value.filter { it.name.contains(command.playlistName, ignoreCase = true) }
                if (matches.isNotEmpty()) {
                    currentTab.value = NavTab.PLAYLISTS
                }
            }
            is VoiceCommand.PlayYear -> {
                val matches = allSongs.value.filter { it.year == command.year }
                if (matches.isNotEmpty()) playSong(matches.first(), matches)
            }
            is VoiceCommand.Unknown -> {
                searchQuery.value = command.rawText
                currentTab.value = NavTab.SEARCH
            }
        }
    }

    private fun setupVoiceListener() {
        // Reserved for voice hardware key hooks
    }

    override fun onCleared() {
        playbackManager.release()
        super.onCleared()
    }
}
