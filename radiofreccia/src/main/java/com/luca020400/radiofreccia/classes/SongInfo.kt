package com.luca020400.radiofreccia.classes

import com.google.gson.annotations.SerializedName
import com.luca020400.radiofreccia.classes.Present
import com.luca020400.radiofreccia.classes.Show

data class SongInfo(
        @SerializedName("show") val show: Show,
        @SerializedName("present") val present: Present?
)