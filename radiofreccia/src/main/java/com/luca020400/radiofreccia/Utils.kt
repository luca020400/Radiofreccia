package com.luca020400.radiofreccia

import android.support.v4.media.MediaDescriptionCompat
import com.luca020400.radiofreccia.classes.Song

object Utils {
    fun getMediaDescription(song: Song?): MediaDescriptionCompat {
        val builder = MediaDescriptionCompat.Builder()
        song?.let {
            it.songInfo.present?.let { present ->
                builder.setMediaId(present.mus_art_id.toString())
                        .setTitle(present.mus_sng_title)
                        .setDescription(present.mus_art_name)
            }
        }
        return builder.build()
    }
}
