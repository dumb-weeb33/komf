package snd.komf.providers.mangabaka.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komf.providers.mangabaka.MangaBakaCover


@Serializable
data class MangaBakaImagesResponse(
    val status: Int,
    val data: List<MangaBakaSeriesImage>,
    val pagination: MangaBakaPagination,
    @SerialName("available_languages") val availableLanguages: List<String> = emptyList(),
)

@Serializable
data class MangaBakaSeriesImage(
    val id: Int,
    @SerialName("series_id") val seriesId: Int,
    val index: String? = null,
    @SerialName("index_numeric") val indexNumeric: Double? = null,
    val type: String,
    val language: String,
    val note: String? = null,          // e.g. "ECC Ediciones edition" — matters, see below
    val image: MangaBakaCover,         // reused as-is from MangaBakaSeries.kt
)

@Serializable
data class MangaBakaPagination(
    val count: Int,
    val next: String? = null,
    val page: Int,
    val limit: Int,
)