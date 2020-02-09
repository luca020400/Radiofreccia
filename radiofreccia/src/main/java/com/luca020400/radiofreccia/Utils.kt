package com.luca020400.radiofreccia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.media.MediaDescriptionCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.luca020400.radiofreccia.classes.Song

object Utils {
    fun getMediaDescription(song: Song): MediaDescriptionCompat {
        val builder = MediaDescriptionCompat.Builder()
        song.songInfo.present?.let { present ->
            builder.setMediaId(present.mus_art_id.toString())
                    .setTitle(present.mus_sng_title)
                    .setDescription(present.mus_art_name)
                    .setMediaUri(Uri.parse(present.mus_sng_itunescoverbig))

        }
        return builder.build()
    }

    fun loadBitmap(context: Context, url: String, callback: PlayerNotificationManager.BitmapCallback) {
        Glide.with(context)
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
