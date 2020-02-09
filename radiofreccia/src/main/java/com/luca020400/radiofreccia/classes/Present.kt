package com.luca020400.radiofreccia.classes

import com.google.gson.annotations.SerializedName

data class Present (
        @SerializedName("class") val class_ : String,
        @SerializedName("duration") val duration : Int,
        @SerializedName("mus_art_id") val mus_art_id : Int,
        @SerializedName("mus_art_name") val mus_art_name : String,
        @SerializedName("musArtGroupIds") val musArtGroupIds : String,
        @SerializedName("mus_sng_id") val mus_sng_id : Int,
        @SerializedName("mus_sng_title") val mus_sng_title : String,
        @SerializedName("mus_sng_itunesurl") val mus_sng_itunesurl : String,
        @SerializedName("mus_sng_itunesreleasedate") val mus_sng_itunesreleasedate : String,
        @SerializedName("mus_sng_itunesalbumname") val mus_sng_itunesalbumname : String,
        @SerializedName("mus_sng_itunescoverbig") val mus_sng_itunescoverbig : String,
        @SerializedName("artistMedia") val artistMedia : String
)