package snd.komf.providers.mangabaka

import snd.komf.providers.mangabaka.api.MangaBakaSeriesImage 

interface MangaBakaDataSource {
    suspend fun search(
        title: String,
        types: List<MangaBakaType>? = null,
    ): List<MangaBakaSeries>

    suspend fun getSeries(id: MangaBakaSeriesId): MangaBakaSeries

    suspend fun getSeriesImages(id: MangaBakaSeriesId, language: String? = null): List<MangaBakaSeriesImage>
}