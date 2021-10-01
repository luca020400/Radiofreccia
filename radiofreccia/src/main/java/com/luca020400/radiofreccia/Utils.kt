package com.luca020400.radiofreccia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat.*
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.luca020400.radiofreccia.classes.Song

object Utils {
    fun getMediaDescription(song: Song): MediaDescriptionCompat {
        val builder = MediaDescriptionCompat.Builder()
        song.songInfo.present?.let { present ->
            val bundle = Bundle().apply {
                putString(METADATA_KEY_TITLE, present.mus_sng_title)
                putString(METADATA_KEY_ARTIST, present.mus_art_name)
                putString(METADATA_KEY_ALBUM, present.mus_sng_itunesalbumname)
                try {
                    putString(
                        METADATA_KEY_ART_URI,
                        Uri.parse(present.mus_sng_itunescoverbig).toString()
                    )
                } catch (e: Exception) {
                }
            }
            builder.setMediaId(present.mus_art_id.toString())
                .setTitle(present.mus_sng_title)
                .setSubtitle(present.mus_art_name)
                .setDescription(present.mus_sng_itunesalbumname)
                .setIconUri(Uri.parse(present.mus_sng_itunescoverbig))
                .setExtras(bundle)
        } ?: run {
            val bundle = Bundle().apply {
                putString(METADATA_KEY_TITLE, song.songInfo.show.prg_title)
                putString(METADATA_KEY_ARTIST, song.songInfo.show.speakers)
                try {
                    putString(
                        METADATA_KEY_ART_URI,
                        Uri.parse(song.songInfo.show.image400).toString()
                    )
                } catch (e: Exception) {
                }
            }
            builder.setMediaId(song.songInfo.show.chaId.toString())
                .setTitle(song.songInfo.show.prg_title)
                .setSubtitle(song.songInfo.show.speakers)
                .setIconUri(Uri.parse(song.songInfo.show.image400))
                .setExtras(bundle)
        }
        return builder.build()
    }

    fun loadBitmap(
        context: Context,
        url: String,
        callback: PlayerNotificationManager.BitmapCallback
    ) {
        GlideApp.with(context)
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
