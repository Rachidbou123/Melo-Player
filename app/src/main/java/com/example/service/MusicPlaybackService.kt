package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidPlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class MusicPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val CHANNEL_ID = "melo_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.melo.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.melo.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.melo.ACTION_NEXT"
        const val ACTION_PREV = "com.example.melo.ACTION_PREV"
        const val ACTION_STOP = "com.example.melo.ACTION_STOP"
        const val ACTION_SEEK = "com.example.melo.ACTION_SEEK"

        var servicePlaybackCallback: ServicePlaybackCallback? = null
    }

    interface ServicePlaybackCallback {
        fun onServicePlay()
        fun onServicePause()
        fun onServiceNext()
        fun onServicePrev()
        fun onServiceSeekTo(positionMs: Long)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(this, "MeloMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        servicePlaybackCallback?.onServicePlay()
                    }

                    override fun onPause() {
                        servicePlaybackCallback?.onServicePause()
                    }

                    override fun onSkipToNext() {
                        servicePlaybackCallback?.onServiceNext()
                    }

                    override fun onSkipToPrevious() {
                        servicePlaybackCallback?.onServicePrev()
                    }

                    override fun onSeekTo(pos: Long) {
                        servicePlaybackCallback?.onServiceSeekTo(pos)
                    }

                    override fun onStop() {
                        try {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        stopSelf()
                    }
                })
                isActive = true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS promote to foreground IMMEDIATELY to prevent ForegroundServiceDidNotStartInTimeException
        val initialNotif = buildMediaNotification(
            title = "Melo Music Player",
            artist = "Ready to play",
            isPlaying = false,
            artwork = null
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotif)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        when (intent?.action) {
            ACTION_PLAY -> servicePlaybackCallback?.onServicePlay()
            ACTION_PAUSE -> servicePlaybackCallback?.onServicePause()
            ACTION_NEXT -> servicePlaybackCallback?.onServiceNext()
            ACTION_PREV -> servicePlaybackCallback?.onServicePrev()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra("extra_seek_pos", 0L)
                servicePlaybackCallback?.onServiceSeekTo(pos)
            }
            ACTION_STOP -> {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    fun updatePlaybackState(song: Song?, isPlaying: Boolean, currentPositionMs: Long, durationMs: Long) {
        val activeSong = song ?: return

        try {
            // Update MediaSession state for system & bluetooth controls
            val stateBuilder = AndroidPlaybackState.Builder()
                .setActions(
                    AndroidPlaybackState.ACTION_PLAY or
                    AndroidPlaybackState.ACTION_PAUSE or
                    AndroidPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidPlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) AndroidPlaybackState.STATE_PLAYING else AndroidPlaybackState.STATE_PAUSED,
                    currentPositionMs,
                    1.0f
                )

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        serviceScope.launch {
            val artworkBitmap = loadArtworkBitmap(activeSong.albumArtUri)

            try {
                // Update Metadata for Lock Screen / Bluetooth
                val metaBuilder = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, activeSong.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, activeSong.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, activeSong.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

                if (artworkBitmap != null) {
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, artworkBitmap)
                }
                mediaSession?.setMetadata(metaBuilder.build())
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            val notification = buildMediaNotification(
                title = activeSong.title,
                artist = activeSong.artist,
                isPlaying = isPlaying,
                artwork = artworkBitmap
            )

            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadArtworkBitmap(artUriString: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (artUriString.isNull_or_blank()) return@withContext null
        try {
            val uri = Uri.parse(artUriString)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun buildMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artwork: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PAUSE }
            val pending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pending
            ).build()
        } else {
            val playIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY }
            val pending = PendingIntent.getService(this, 3, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                pending
            ).build()
        }

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val sessionTokenCompat = mediaSession?.sessionToken?.let {
            try {
                MediaSessionCompat.Token.fromToken(it)
            } catch (e: Throwable) {
                null
            }
        }

        val mediaStyle = MediaStyle()
            .setMediaSession(sessionTokenCompat)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setStyle(mediaStyle)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Melo Music Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Media controls for current music playback"
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            mediaSession?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        mediaSession = null
        super.onDestroy()
    }
}
