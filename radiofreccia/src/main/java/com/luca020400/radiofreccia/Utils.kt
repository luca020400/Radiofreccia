package com.luca020400.radiofreccia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat.*
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.util.Util
import com.luca020400.radiofreccia.classes.Song

object Utils {
    fun getMediaDescription(song: Song): MediaDescriptionCompat {
        val builder = MediaDescriptionCompat.Builder()
        song.songInfo?.let {
            it.present?.let { present ->
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
                    putString(METADATA_KEY_TITLE, it.show.prg_title)
                    putString(METADATA_KEY_ARTIST, it.show.speakers)
                    try {
                        putString(
                            METADATA_KEY_ART_URI,
                            Uri.parse(it.show.image400).toString()
                        )
                    } catch (e: Exception) {
                    }
                }
                builder.setMediaId(it.show.chaId.toString())
                    .setTitle(it.show.prg_title)
                    .setSubtitle(it.show.speakers)
                    .setIconUri(Uri.parse(it.show.image400))
                    .setExtras(bundle)
            }
        }
        return builder.build()
    }

    fun loadBitmap(
        context: Context,
        url: String,
        callback: (Bitmap) -> Unit
    ) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .target(
                onStart = {
                    // Handle the placeholder drawable.
                },
                onSuccess = {
                    callback(it.toBitmap())
                },
                onError = {
                    // Handle the error drawable.
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
