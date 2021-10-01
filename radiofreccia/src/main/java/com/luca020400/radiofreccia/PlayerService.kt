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
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import com.luca020400.radiofreccia.classes.Song

class PlayerService : Service() {
    private lateinit var audioFocusPlayer: ExoPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var song: Song? = null

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            audioFocusPlayer.playWhenReady = false
        }
    }

    private val mediaSource by lazy {
        val mediaItem: MediaItem = MediaItem.Builder()
            .setUri(Uri.parse(MEDIA_URL))
            .build()

        val dataSourceFactory = DefaultDataSourceFactory(
            this, Util.getUserAgent(this, getString(R.string.app_name))
        )
        HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        const val PLAYBACK_NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "audio_radiofreccia"
        const val MEDIA_URL =
            "https://rtl-radio6-stream.thron.com/live/radio6/radio6/chunklist.m3u8"
    }

    override fun onCreate() {
        super.onCreate()

        val audioManager = getSystemService(AudioManager::class.java) as AudioManager
        val audioAttributes = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build()
        audioFocusPlayer = AudioFocusWrapper(
            audioAttributes,
            audioManager,
            SimpleExoPlayer.Builder(this).build()
        )
        audioFocusPlayer.addListener(object : Listener {
            override fun onMetadata(metadata: Metadata) {
                try {
                    val string = metadata.get(0).toString()
                    val cutString = string.substring(string.indexOf("{\"songInfo\""))
                    with(Gson().fromJson(cutString, Song::class.java)) {
                        if (this == song) return
                        song = this
                        playerNotificationManager.invalidate()
                        mediaSessionConnector.invalidateMediaSessionMetadata()
                    }
                } catch (e: Exception) {
                    // Things can go horribly wrong here
                    // Do nothing if we fail
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isBehindLiveWindowOrHttpError(error)) {
                    audioFocusPlayer.seekToDefaultPosition()
                    audioFocusPlayer.prepare()
                }
            }

            fun isBehindLiveWindowOrHttpError(e: PlaybackException): Boolean {
                if (e !is ExoPlaybackException) {
                    return false
                }

                var cause: Throwable? = e.sourceException
                while (cause != null) {
                    if (cause is BehindLiveWindowException || cause is HttpDataSource.HttpDataSourceException) {
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

        val playerNotificationManagerBuilder = PlayerNotificationManager.Builder(
            this,
            PLAYBACK_NOTIFICATION_ID,
            PLAYBACK_CHANNEL_ID
        ).setChannelNameResourceId(R.string.playback_channel_name)
            .setChannelDescriptionResourceId(R.string.playback_channel_description)
            .setMediaDescriptionAdapter(
                object : MediaDescriptionAdapter {
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
                            Utils.loadBitmap(
                                this@PlayerService,
                                it.songInfo.present?.mus_sng_itunescoverbig
                                    ?: it.songInfo.show.image400, callback
                            )
                        }
                        return null
                    }
                }).setNotificationListener(
                object : PlayerNotificationManager.NotificationListener {
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
        playerNotificationManager.setPlayer(audioFocusPlayer)

        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG)
        mediaSession.isActive = true
        playerNotificationManager.setMediaSessionToken(mediaSession.sessionToken)

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
            override fun getMediaDescription(player: Player, windowIndex: Int) =
                song?.let {
                    Utils.getMediaDescription(it)
                } ?: MediaDescriptionCompat.Builder().build()
        })
        mediaSessionConnector.setPlayer(audioFocusPlayer)

        registerReceiver(mediaReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaSessionConnector.setPlayer(null)
        playerNotificationManager.setPlayer(null)
        audioFocusPlayer.release()
        unregisterReceiver(mediaReceiver)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
}