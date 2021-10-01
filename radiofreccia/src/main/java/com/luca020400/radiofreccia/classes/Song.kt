package com.luca020400.radiofreccia.classes

import com.google.gson.annotations.SerializedName

data class Song(
    @SerializedName("songInfo") val songInfo: SongInfo
)