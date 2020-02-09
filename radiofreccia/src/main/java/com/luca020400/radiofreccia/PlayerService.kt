package com.luca020400.radiofreccia

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.AudioAttributesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import com.luca020400.radiofreccia.classes.Song

class PlayerService : Service() {
    private lateinit var audioFocusPlayer: ExoPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var song: Song? = null

    private val mediaSource by lazy {
        HlsMediaSource.Factory(DefaultDataSourceFactory(this,
                Util.getUserAgent(this, getString(R.string.app_name))))
                .createMediaSource(Uri.parse("https://rtl-radio6-stream.thron.com/live/radio6/radio6/chunklist.m3u8"))
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        const val PLAYBACK_NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "audio_radiofreccia"
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
        audioFocusPlayer.addListener(object : Player.EventListener {
            override fun onPlayerError(e: ExoPlaybackException) {
                if (isBehindLiveWindow(e)) {
                    audioFocusPlayer.prepare(mediaSource, true, false)
                }
            }

            fun isBehindLiveWindow(e: ExoPlaybackException): Boolean {
                if (e.type != ExoPlaybackException.TYPE_SOURCE) {
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
        audioFocusPlayer.metadataComponent?.addMetadataOutput {
            for (i in 0 until it.length()) {
                try {
                    val string = it.toString()
                    val cutString = string.substring(40, string.length - 1)
                    song = Gson().fromJson(cutString, Song::class.java)
                    playerNotificationManager.invalidate()
                    mediaSessionConnector.invalidateMediaSessionMetadata()
                } catch (e: Exception) {
                    // Things can go horribly wrong here
                    // Do nothing if we fail
                }
            }
        }
        audioFocusPlayer.prepare(mediaSource)
        audioFocusPlayer.playWhenReady = true

        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                this,
                PLAYBACK_CHANNEL_ID,
                R.string.playback_channel_name,
                R.string.playback_channel_description,
                PLAYBACK_NOTIFICATION_ID,
                object : MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): String {
                        song?.let {
                            it.songInfo.present?.let { present ->
                                return present.mus_sng_title
                            }
                        }
                        return ""
                    }

                    override fun createCurrentContentIntent(player: Player): PendingIntent? {
                        return null
                    }

                    override fun getCurrentContentText(player: Player): String? {
                        song?.let {
                            it.songInfo.present?.let { present ->
                                return present.mus_art_name
                            }
                        }
                        return null
                    }

                    override fun getCurrentLargeIcon(player: Player, callback: BitmapCallback): Bitmap? {
                        song?.let {
                            it.songInfo.present?.let { present ->
                                loadBitmap(present.mus_sng_itunescoverbig, callback)
                            }
                        }
                        return null
                    }
                },
                object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                        startForeground(notificationId, notification)
                    }

                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        stopSelf()
                    }
                })
        playerNotificationManager.setUseNavigationActions(false)
        playerNotificationManager.setFastForwardIncrementMs(0)
        playerNotificationManager.setRewindIncrementMs(0)
        playerNotificationManager.setPlayer(audioFocusPlayer)

        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG)
        mediaSession.isActive = true
        playerNotificationManager.setMediaSessionToken(mediaSession.sessionToken)

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
            override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
                return Utils.getMediaDescription(song)
            }
        })
        mediaSessionConnector.setPlayer(audioFocusPlayer)
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaSessionConnector.setPlayer(null)
        playerNotificationManager.setPlayer(null)
        audioFocusPlayer.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun loadBitmap(url: String, callback: BitmapCallback) {
        Glide.with(this)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        callback.onBitmap(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
    }
}
