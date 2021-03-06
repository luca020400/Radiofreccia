package com.luca020400.radiofreccia

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.util.Util

class EmptyActivity : AppCompatActivity() {

    // Android lifecycle hooks.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createPlayer()

        finish()
    }

    // ExoPlayer related functions.
    private fun createPlayer() {
        val intent = Intent(this, PlayerService::class.java)
        Util.startForegroundService(this, intent)
    }
}