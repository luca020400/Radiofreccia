package com.luca020400.radiofreccia

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.gson.Gson
import com.luca020400.radiofreccia.classes.Song

class PlayerService : Service() {
    private lateinit var wrapperPlayer: ForwardingPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var song: Song? = null

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            wrapperPlayer.playWhenReady = false
        }
    }

    private val mediaSource by lazy {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(MEDIA_URL))
            .build()

        val dataSourceFactory = DefaultHttpDataSource.Factory()

        HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        const val PLAYBACK_NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "audio_radiofreccia"
        const val MEDIA_URL =
            "https://streamcdnm36-dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/audiostream/S3160845/0tuSetc8UFkF/playlist_audio.m3u8"
    }

    override fun onCreate() {
        super.onCreate()

        val audioManager = getSystemService(AudioManager::class.java)
        val audioAttributes = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build()
        val audioFocusPlayer = AudioFocusPlayer(
            audioAttributes,
            audioManager,
            ExoPlayer.Builder(this).build()
        )
        audioFocusPlayer.addListener(object : Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is TextInformationFrame && entry.id == "TEXT") {
                        try {
                            Gson().fromJson(entry.value, Song::class.java).let {
                                if (it == song) return
                                song = it
                                playerNotificationManager.invalidate()
                                mediaSessionConnector.invalidateMediaSessionMetadata()
                            }
                        } catch (e: Exception) {
                            // Things can go horribly wrong here
                            // Do nothing if we fail
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isBehindLiveWindow(error)) {
                    audioFocusPlayer.seekToDefaultPosition()
                    audioFocusPlayer.prepare()
                }
            }

            fun isBehindLiveWindow(e: PlaybackException): Boolean {
                if (e !is ExoPlaybackException) {
                    return false
                }

                var cause: Throwable? = e.sourceException
                while (cause != null) {
                    if (cause is BehindLiveWindowException) {
                        return true
                    }
                    cause = cause.cause
                }
                return false
            }
        })
        audioFocusPlayer.setMediaSource(mediaSource)
        audioFocusPlayer.prepare()
        audioFocusPlayer.playWhenReady = true

        wrapperPlayer = object : ForwardingPlayer(audioFocusPlayer) {
            override fun getAvailableCommands() =
                Player.Commands.Builder()
                    .add(COMMAND_STOP)
                    .add(COMMAND_PLAY_PAUSE)
                    .build()

        }

        val playerNotificationManagerBuilder = PlayerNotificationManager.Builder(
            this,
            PLAYBACK_NOTIFICATION_ID,
            PLAYBACK_CHANNEL_ID
        ).setChannelNameResourceId(R.string.playback_channel_name)
            .setChannelDescriptionResourceId(R.string.playback_channel_description)
            .setMediaDescriptionAdapter(object : MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    song?.let {
                        return it.songInfo.present?.mus_sng_title ?: it.songInfo.show.prg_title
                    }
                    return ""
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? = null

                override fun getCurrentContentText(player: Player): CharSequence? {
                    song?.let {
                        return it.songInfo.present?.mus_art_name ?: it.songInfo.show.speakers
                    }
                    return null
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: BitmapCallback
                ): Bitmap? {
                    song?.let {
                        val url = it.songInfo.present?.mus_sng_itunescoverbig
                            ?: it.songInfo.show.image400
                        Utils.loadBitmap(
                            this@PlayerService, url
                        ) { bitmap ->
                            callback.onBitmap(bitmap)
                        }
                    }
                    return null
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    startForeground(notificationId, notification)
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    stopSelf()
                }
            })
        playerNotificationManager = playerNotificationManagerBuilder.build()
        playerNotificationManager.setUseFastForwardAction(false)
        playerNotificationManager.setUseFastForwardActionInCompactView(false)
        playerNotificationManager.setUseNextAction(false)
        playerNotificationManager.setUsePreviousAction(false)
        playerNotificationManager.setUseRewindAction(false)
        playerNotificationManager.setUseRewindActionInCompactView(false)
        playerNotificationManager.setUseStopAction(true)
        playerNotificationManager.setPlayer(wrapperPlayer)

        val mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG)
        mediaSession.isActive = true
        playerNotificationManager.setMediaSessionToken(mediaSession.sessionToken)

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
            override fun getSupportedQueueNavigatorActions(player: Player) = 0L

            override fun getMediaDescription(player: Player, windowIndex: Int) =
                song?.let {
                    Utils.getMediaDescription(it)
                } ?: MediaDescriptionCompat.Builder().build()
        })
        mediaSessionConnector.setPlayer(wrapperPlayer)

        registerReceiver(mediaReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        mediaSessionConnector.setPlayer(null)
        mediaSessionConnector.mediaSession.release()
        playerNotificationManager.setPlayer(null)
        wrapperPlayer.release()
        unregisterReceiver(mediaReceiver)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
}