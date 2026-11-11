package com.onsu.player

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "PlaybackService"

        internal fun createDataSourceFactory(context: Context): DefaultDataSource.Factory {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent("MixtapeMusic/1.0 (Android; Media3)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
            return DefaultDataSource.Factory(context, http)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Dispatch content:// and file:// to the device resolver; retain the custom
        // HTTP factory for online streams. An HTTP-only factory cannot play local audio.
        val dataSourceFactory = createDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // handleAudioBecomingNoisy = true
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Auto recovery on track failure
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer playback error: [${error.errorCodeName}] ${error.message}", error)
                // If a source/HTTP error occurs on a track, auto advance to next track so player doesn't stall
                // Do not silently replace a selected local file with another song.
                // The controller exposes the failure so the UI can explain it.
                if (player.currentMediaItem?.localConfiguration?.uri?.scheme in listOf("http", "https") &&
                    player.hasNextMediaItem()
                ) {
                    Log.w(TAG, "Auto-skipping failing track to next available media item")
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        val sessionActivityPendingIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { sessionIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        mediaSession = MediaSession.Builder(this, player)
            .apply {
                if (sessionActivityPendingIntent != null) {
                    setSessionActivity(sessionActivityPendingIntent)
                }
            }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
