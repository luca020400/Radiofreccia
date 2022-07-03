package com.luca020400.radiofreccia

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.luca020400.radiofreccia.classes.Song

class MainActivity : ComponentActivity() {
    private val model: RadioViewModel by viewModels()
    private var service: Messenger? = null
    private var serviceBound = false

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                PlayerService.MSG_SONG -> model.update(msg.obj as Song)
                else -> super.handleMessage(msg)
            }
        }
    })

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            this@MainActivity.service = Messenger(service).also {
                try {
                    val msg = Message.obtain(
                        null,
                        PlayerService.MSG_REGISTER_CLIENT
                    )
                    msg.replyTo = messenger
                    it.send(msg)
                } catch (e: RemoteException) {
                    // In this case the service has crashed before we could even
                    // do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted)
                    // so there is no need to do anything here.
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    private fun doBindService() {
        bindService(
            Intent(this, PlayerService::class.java),
            connection, BIND_AUTO_CREATE
        )
        serviceBound = true
    }

    private fun doUnbindService() {
        service?.let {
            try {
                val msg = Message.obtain(
                    null,
                    PlayerService.MSG_UNREGISTER_CLIENT
                )
                msg.replyTo = messenger
                it.send(msg)
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
        }
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }

    private fun pause() {
        service?.let {
            try {
                val msg = Message.obtain(
                    null,
                    PlayerService.MSG_PAUSE
                )
                msg.replyTo = messenger
                it.send(msg)
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        doBindService()

        setContent {
            RadiofrecciaApp(pause = { pause() })
        }
    }

    override fun onDestroy() {
        doUnbindService()
        super.onDestroy()
    }
}

class RadioViewModel : ViewModel() {
    var song by mutableStateOf(Song(null))

    fun update(song: Song) {
        this.song = song
    }
}

@Composable
fun RadiofrecciaApp(
    pause: () -> Unit,
) {
    RadioFreccia(pause)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioFreccia(
    pause: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name)
                    )
                },
                actions = {
                    IconButton(onClick = { /* doSomething() */ }) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Localized description"
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { /* "Open nav drawer" */ }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Localized description"
                        )
                    }
                }
            )
        },
        content = { innerPadding ->
            Radio(innerPadding = innerPadding, pause = pause)
        }
    )
}

@Composable
fun Radio(
    viewModel: RadioViewModel = viewModel(),
    innerPadding: PaddingValues,
    pause: () -> Unit,
) {
    val song = viewModel.song
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
    ) {
        song.songInfo?.let {
            it.present?.let { present ->
                Text(
                    text = "Title: ${present.mus_sng_title}",
                )
                Text(
                    text = "Album: ${present.mus_sng_itunesalbumname}",
                )
                Text(
                    text = "Artist: ${present.mus_art_name}",
                )
                AsyncImage(
                    model = present.mus_sng_itunescoverbig,
                    contentDescription = null
                )
            } ?: run {
                Text(
                    text = "Program: ${it.show.prg_title}",
                )
                Text(
                    text = "Speakers: ${it.show.speakers}",
                )
                AsyncImage(
                    model = it.show.image400,
                    contentDescription = null
                )
            }
        }
        OutlinedButton(onClick = {
            pause()
        }) {
            Text(text = "Pause/Continue")
        }
    }
}