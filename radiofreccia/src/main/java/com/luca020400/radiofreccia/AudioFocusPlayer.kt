/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luca020400.radiofreccia

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.ExoPlayer

class AudioFocusPlayer(
    private val audioAttributes: AudioAttributesCompat,
    private val audioManager: AudioManager,
    private val player: ExoPlayer
) : ExoPlayer by player {
    companion object {
        private const val MEDIA_VOLUME_DEFAULT = 1.0f
        private const val MEDIA_VOLUME_DUCK = 0.2f
    }

    private var shouldPlayWhenReady = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldPlayWhenReady || player.playWhenReady) {
                    player.playWhenReady = true
                    player.volume = MEDIA_VOLUME_DEFAULT
                }
                shouldPlayWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.playWhenReady) {
                    player.volume = MEDIA_VOLUME_DUCK
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldPlayWhenReady = player.playWhenReady
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandonAudioFocus()
            }
        }
    }

    private val audioFocusRequest by lazy { buildFocusRequest() }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) requestAudioFocus() else abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        // Call the listener whenever focus is granted - even the first time!
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            shouldPlayWhenReady = true
            audioFocusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        player.playWhenReady = false
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun buildFocusRequest(): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes.unwrap() as AudioAttributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
}