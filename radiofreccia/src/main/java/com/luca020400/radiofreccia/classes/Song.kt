package com.luca020400.radiofreccia.classes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Song(
    @Json(name = "songInfo") val songInfo: SongInfo
) {
    @JsonClass(generateAdapter = true)
    data class SongInfo(
        @Json(name = "show") val show: Show,
        @Json(name = "present") val present: Present?
    ) {
        @JsonClass(generateAdapter = true)
        data class Show(
            @Json(name = "sch_time_from") val sch_time_from: Int,
            @Json(name = "sch_time_to") val sch_time_to: Int,
            @Json(name = "prg_title") val prg_title: String,
            @Json(name = "speakers") val speakers: String,
            @Json(name = "ChaId") val chaId: Int,
            @Json(name = "image170") val image170: String,
            @Json(name = "image400") val image400: String,
            @Json(name = "ScheduleId") val scheduleId: Int?
        )

        @JsonClass(generateAdapter = true)
        data class Present(
            @Json(name = "class") val class_: String,
            @Json(name = "duration") val duration: Int,
            @Json(name = "mus_art_id") val mus_art_id: Int,
            @Json(name = "mus_art_name") val mus_art_name: String,
            @Json(name = "musArtGroupIds") val musArtGroupIds: String?,
            @Json(name = "mus_sng_id") val mus_sng_id: Int,
            @Json(name = "mus_sng_title") val mus_sng_title: String,
            @Json(name = "mus_sng_itunesurl") val mus_sng_itunesurl: String,
            @Json(name = "mus_sng_itunesreleasedate") val mus_sng_itunesreleasedate: String,
            @Json(name = "mus_sng_itunesalbumname") val mus_sng_itunesalbumname: String,
            @Json(name = "mus_sng_itunescoverbig") val mus_sng_itunescoverbig: String,
            @Json(name = "artistMedia") val artistMedia: String?
        )
    }
}