package com.luca020400.radiofreccia.classes

import com.google.gson.annotations.SerializedName

data class Show(
        @SerializedName("sch_time_from") val sch_time_from: Int,
        @SerializedName("sch_time_to") val sch_time_to: Int,
        @SerializedName("prg_title") val prg_title: String,
        @SerializedName("speakers") val speakers: String,
        @SerializedName("ChaId") val chaId: Int,
        @SerializedName("image170") val image170: String,
        @SerializedName("image400") val image400: String,
        @SerializedName("ScheduleId") val scheduleId: Int
)