package snd.komf.providers.mangabaka.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import snd.komf.providers.mangabaka.MangaBakaDataSource
import snd.komf.providers.mangabaka.MangaBakaSeries
import snd.komf.providers.mangabaka.MangaBakaSeriesId
import snd.komf.providers.mangabaka.MangaBakaType

class MangaBakaApiClient(private val ktor: HttpClient) : MangaBakaDataSource {
    private val baseUrl = "https://api.mangabaka.org"

    override suspend fun search(title: String, types: List<MangaBakaType>?): List<MangaBakaSeries> {
        return ktor.get("${baseUrl}/v1/series/search") {
            parameter("q", title)
            parameter("content_rating", "safe")
            parameter("content_rating", "suggestive")
            parameter("content_rating", "erotica")
            parameter("content_rating", "pornographic")
            types?.forEach { parameter("type", it.name.lowercase()) }
        }.body<MangaBakaSearchResponse>().data
    }

    override suspend fun getSeries(id: MangaBakaSeriesId): MangaBakaSeries {
        return ktor.get("${baseUrl}/v1/series/${id}").body<MangaBakaResponse>().data
    }

    override suspend fun getSeriesImages(id: MangaBakaSeriesId, language: String?): List<MangaBakaSeriesImage> {
        val allImages = mutableListOf<MangaBakaSeriesImage>()
        var nextUrl: String? = "${baseUrl}/v1/series/${id}/images"
        var firstRequest = true
        var pageCount = 0

        while (nextUrl != null && pageCount < 50) {
            pageCount++
            val response = if (firstRequest) {
                ktor.get(nextUrl) {
                    parameter("type", "volume")
                    language?.let { parameter("language", it) }
                }
            } else {
                ktor.get(nextUrl)
            }.body<MangaBakaImagesResponse>()

            allImages += response.data
            nextUrl = response.pagination.next
            firstRequest = false
        }

        return allImages
    }
}