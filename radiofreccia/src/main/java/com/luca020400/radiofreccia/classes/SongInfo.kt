package com.luca020400.radiofreccia.classes

import com.google.gson.annotations.SerializedName

data class SongInfo(
        @SerializedName("show") val show: Show,
        @SerializedName("present") val present: Present?
)