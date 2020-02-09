package com.luca020400.radiofreccia

import android.support.v4.media.MediaDescriptionCompat
import com.luca020400.radiofreccia.classes.Song

object Utils {
    fun getMediaDescription(song: Song?): MediaDescriptionCompat {
        val builder = MediaDescriptionCompat.Builder()
        song?.let {
            builder.setMediaId(song.songInfo.present.mus_art_id.toString())
                    .setTitle(song.songInfo.present.mus_sng_title)
                    .setDescription(song.songInfo.present.mus_art_name)
        }
        return builder.build()
    }
}