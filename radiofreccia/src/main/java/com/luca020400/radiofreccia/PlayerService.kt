package com.luca020400.radiofreccia

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.os.IBinder.DeathRecipient
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.audio.AudioAttributes
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
import com.luca020400.radiofreccia.classes.Song
import com.luca020400.radiofreccia.classes.SongJsonAdapter
import com.squareup.moshi.Moshi
import java.lang.ref.WeakReference


class PlayerService : Service() {
    private lateinit var wrapperPlayer: ForwardingPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var song: Song? = null

    private val mediaSource by lazy {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(MEDIA_URL))
            .build()

        val dataSourceFactory = DefaultHttpDataSource.Factory()

        HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    private val lock = Any()

    @GuardedBy("lock")
    private val clients = hashMapOf<IBinder, RecorderClient>()
    private val handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> registerClient(
                    RecorderClient(
                        msg.replyTo,
                        msg.replyTo.binder
                    )
                )
                MSG_UNREGISTER_CLIENT -> synchronized(lock) {
                    unregisterClientLocked(msg.replyTo.binder)
                }
                MSG_PAUSE -> wrapperPlayer.playWhenReady = !wrapperPlayer.playWhenReady
                else -> super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(handler)

    private class RecorderClient constructor(private val messenger: Messenger, val token: IBinder) {
        lateinit var deathRecipient: DeathRecipient
        fun send(message: Message?) {
            try {
                messenger.send(message)
            } catch (e: RemoteException) {
            }
        }
    }

    private class PlayerClientDeathRecipient(
        service: PlayerService,
        private val client: RecorderClient,
    ) : DeathRecipient {
        val serviceRef: WeakReference<PlayerService>
        override fun binderDied() {
            val service: PlayerService = serviceRef.get() ?: return
            synchronized(service.lock) {
                service.unregisterClientLocked(client.token)
            }
        }

        init {
            client.deathRecipient = this
            serviceRef = WeakReference(service)
        }
    }

    private fun registerClient(client: RecorderClient) {
        synchronized(lock) {
            if (unregisterClientLocked(client.token)) {
                Log.i(TAG, "Client was already registered, override it.")
            }
            clients.put(client.token, client)
        }
        try {
            client.token.linkToDeath(PlayerClientDeathRecipient(this, client), 0)
        } catch (ignored: RemoteException) {
            // Already gone
        }
    }

    private fun unregisterClients() {
        synchronized(lock) {
            for (client in clients.values) {
                client.token.unlinkToDeath(client.deathRecipient, 0)
            }
        }
    }

    private fun notifySong(song: Song) {
        val clients: List<RecorderClient>
        synchronized(lock) {
            clients = ArrayList(this.clients.values)
        }
        for (client in clients) {
            client.send(handler.obtainMessage(MSG_SONG, song))
        }
    }

    @GuardedBy("lock")
    private fun unregisterClientLocked(token: IBinder): Boolean {
        val client = clients.remove(token) ?: return false
        token.unlinkToDeath(client.deathRecipient, 0)
        return true
    }

    companion object {
        const val TAG = "Player"

        const val MSG_REGISTER_CLIENT = 0
        const val MSG_UNREGISTER_CLIENT = 1
        const val MSG_SONG = 2
        const val MSG_PAUSE = 3
        const val MSG_STOP = 4

        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        const val PLAYBACK_NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "audio_radiofreccia"
        const val MEDIA_URL =
            "https://streamcdnm36-dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/audiostream/S3160845/0tuSetc8UFkF/playlist_audio.m3u8"
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.addListener(object : Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is TextInformationFrame && entry.id == "TEXT") {
                        try {
                            val moshi: Moshi = Moshi.Builder().build()
                            SongJsonAdapter(moshi).fromJson(entry.value).let {
                                if (it == null || it == song) return
                                song = it
                                notifySong(it)
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
                    player.seekToDefaultPosition()
                    player.prepare()
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
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        wrapperPlayer = object : ForwardingPlayer(player) {
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
                        return it.songInfo?.present?.mus_sng_title ?: it.songInfo?.show?.prg_title
                        ?: ""
                    }
                    return ""
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? = null

                override fun getCurrentContentText(player: Player): CharSequence? {
                    song?.let {
                        return it.songInfo?.present?.mus_art_name ?: it.songInfo?.show?.speakers
                    }
                    return null
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: BitmapCallback
                ): Bitmap? {
                    song?.let {
                        val url = it.songInfo?.present?.mus_sng_itunescoverbig
                            ?: it.songInfo?.show?.image400 ?: return null
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
    }

    override fun onDestroy() {
        unregisterClients()

        mediaSessionConnector.setPlayer(null)
        mediaSessionConnector.mediaSession.release()
        playerNotificationManager.setPlayer(null)
        wrapperPlayer.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
}