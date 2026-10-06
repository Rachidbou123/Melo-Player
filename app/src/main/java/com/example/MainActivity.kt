package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AddToPlaylistBottomSheet
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.ShareDialog
import com.example.ui.components.VoiceOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MetadataEditScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.NavTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MusicPlayerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MusicPlayerApp(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isQueueOpen by viewModel.isQueueSheetOpen.collectAsState()
    val isMetadataEditorOpen by viewModel.isMetadataEditorOpen.collectAsState()
    val songToEdit by viewModel.songToEdit.collectAsState()
    val addToPlaylistSong by viewModel.addToPlaylistSheetSong.collectAsState()
    val isVoiceOpen by viewModel.isVoiceOverlayOpen.collectAsState()
    val isShareOpen by viewModel.isShareSheetOpen.collectAsState()
    val shareSong by viewModel.shareItem.collectAsState()
    val isEqualizerOpen by viewModel.isEqualizerOpen.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // Request permissions for audio and microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            viewModel.scanDeviceStorage()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Mini Player docked flush above navigation
                MiniPlayer(
                    playbackState = playbackState,
                    onMiniPlayerClick = { viewModel.isPlayerExpanded.value = true },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.playNext() }
                )

                // Navigation Bar: HOME, SEARCH, LIBRARY, PLAYLISTS, SETTINGS
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    val navItems = listOf(
                        NavTabItem(NavTab.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
                        NavTabItem(NavTab.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
                        NavTabItem(NavTab.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
                        NavTabItem(NavTab.PLAYLISTS, "Playlists", Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic),
                        NavTabItem(NavTab.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
                    )

                    navItems.forEach { item ->
                        val isSelected = currentTab == item.tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.currentTab.value = item.tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${item.label.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                NavTab.HOME -> HomeScreen(viewModel = viewModel)
                NavTab.SEARCH -> SearchScreen(viewModel = viewModel)
                NavTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                NavTab.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }

            // Full Screen Now Playing
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                PlayerScreen(
                    viewModel = viewModel,
                    onDismiss = { viewModel.isPlayerExpanded.value = false }
                )
            }

            // Queue Screen
            AnimatedVisibility(
                visible = isQueueOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                QueueScreen(
                    viewModel = viewModel,
                    onDismiss = { viewModel.isQueueSheetOpen.value = false }
                )
            }

            // Add To Playlist Bottom Sheet
            addToPlaylistSong?.let { song ->
                AddToPlaylistBottomSheet(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { viewModel.addToPlaylistSheetSong.value = null }
                )
            }

            // Metadata Editor Screen
            AnimatedVisibility(
                visible = isMetadataEditorOpen && songToEdit != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                songToEdit?.let { song ->
                    MetadataEditScreen(
                        song = song,
                        viewModel = viewModel,
                        onDismiss = {
                            viewModel.isMetadataEditorOpen.value = false
                            viewModel.songToEdit.value = null
                        }
                    )
                }
            }

            // Voice Assistant Overlay
            if (isVoiceOpen) {
                VoiceOverlay(
                    voiceManager = viewModel.voiceManager,
                    onDismiss = { viewModel.cancelVoiceCommand() },
                    onSamplePromptClick = { prompt ->
                        viewModel.cancelVoiceCommand()
                        if (prompt.contains("fav", true)) {
                            val favs = viewModel.favoriteSongs.value
                            if (favs.isNotEmpty()) viewModel.playSong(favs.random(), favs.shuffled())
                        } else if (prompt.contains("next", true)) {
                            viewModel.playNext()
                        } else if (prompt.contains("volume", true)) {
                            viewModel.playbackManager.setVolume(1.0f)
                        } else if (prompt.contains("pause", true)) {
                            if (playbackState.isPlaying) viewModel.togglePlayPause()
                        }
                    }
                )
            }

            // Share Dialog
            if (isShareOpen && shareSong != null) {
                shareSong?.let { song ->
                    ShareDialog(
                        song = song,
                        onDismiss = {
                            viewModel.isShareSheetOpen.value = false
                            viewModel.shareItem.value = null
                        }
                    )
                }
            }

            // Equalizer & Audio FX Dialog
            if (isEqualizerOpen) {
                EqualizerDialog(
                    playbackState = playbackState,
                    onPresetSelected = { preset -> viewModel.setEqualizerPreset(preset) },
                    onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
                    onDismiss = { viewModel.isEqualizerOpen.value = false }
                )
            }
        }
    }
}

private data class NavTabItem(
    val tab: NavTab,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
