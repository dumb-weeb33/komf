package snd.komf.providers.mangabaka

import io.github.reactivecircus.cache4k.Cache
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.contentType
import snd.komf.model.Image
import snd.komf.model.MatchQuery
import snd.komf.model.MediaType
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataProvider
import snd.komf.util.NameSimilarityMatcher
import kotlin.time.Duration.Companion.minutes
import snd.komf.providers.mangabaka.api.MangaBakaSeriesImage

class MangaBakaMetadataProvider(
    private val dataSource: MangaBakaDataSource,
    private val metadataMapper: MangaBakaMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val coverFetchClient: HttpClient?,
    mediaType: MediaType,
    private val fetchVolumeCovers: Boolean,
    private val volumeCoverLanguage: String?,
) : MetadataProvider {
    private val seriesTypes: List<MangaBakaType> = when (mediaType) {
        MediaType.MANGA -> listOf(
            MangaBakaType.MANGA,
            MangaBakaType.MANHWA,
            MangaBakaType.MANHUA,
            MangaBakaType.OEL,
            MangaBakaType.OTHER
        )

        MediaType.NOVEL -> listOf(MangaBakaType.NOVEL)
        MediaType.COMIC -> listOf(MangaBakaType.OEL, MangaBakaType.OTHER)
        MediaType.WEBTOON -> listOf(MangaBakaType.MANHUA, MangaBakaType.MANHWA)
    }

    private val cache = Cache.Builder<MangaBakaSeriesId, MangaBakaSeries>()
        .expireAfterWrite(30.minutes)
        .build()

    override fun providerName() = CoreProviders.MANGA_BAKA

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val id = seriesId.toMangaBakaId()
        val series = cache.get(id) { dataSource.getSeries(id) }
        val cover = fetchCover(series)
        val volumeImages = if (fetchVolumeCovers)
            imagesCache.get(id) { dataSource.getSeriesImages(id, volumeCoverLanguage) }
        else emptyList()
        return metadataMapper.toSeriesMetadata(series, cover, volumeImages)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? {
        val id = seriesId.toMangaBakaId()
        val series = cache.get(id) { dataSource.getSeries(id) }
        return fetchCover(series)
    }

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val id = seriesId.toMangaBakaId()
        val images = imagesCache.get(id) { dataSource.getSeriesImages(id, volumeCoverLanguage) }
        val match = images.firstOrNull { it.id.toString() == bookId.id }
        val image = match?.image?.x350?.x1?.let { url ->
            coverFetchClient?.let { client ->
                val response = client.get(url)
                Image(response.body(), response.contentType()?.let { "${it.contentType}/${it.contentSubtype}" })
            }
        }
        return metadataMapper.toBookMetadata(image)
    }

    override suspend fun searchSeries(
        seriesName: String,
        limit: Int
    ): Collection<SeriesSearchResult> {
        val results = dataSource.search(
            title = seriesName,
            types = seriesTypes,
        )
        results.forEach { cache.put(it.id, it) }

        return results.take(limit).map { metadataMapper.toSeriesSearchResult(it) }
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val seriesName = matchQuery.seriesName
        val searchResults = dataSource.search(seriesName.take(400), seriesTypes)
        searchResults.forEach { cache.put(it.id, it) }

        val match = searchResults.firstOrNull { series ->
            val secondaryTitles = series.secondaryTitles
                ?.flatMap { titles -> titles.value?.map { it.title } ?: emptyList() }
                ?: emptyList()

            val titles = listOfNotNull(
                series.title,
                series.nativeTitle,
                series.romanizedTitle,
            ) + secondaryTitles

            nameMatcher.matches(seriesName, titles)
        }

        return match?.let { series -> metadataMapper.toSeriesMetadata(series, fetchCover(series)) }
    }

    private suspend fun fetchCover(series: MangaBakaSeries): Image? {
        if (coverFetchClient == null || series.cover.x350?.x1 == null) return null

        val response = coverFetchClient.get(series.cover.x350.x1)
        return Image(
            response.body(),
            response.contentType()?.let { "${it.contentType}/${it.contentSubtype}" }
        )
    }

    private val imagesCache = Cache.Builder<MangaBakaSeriesId, List<MangaBakaSeriesImage>>()
        .expireAfterWrite(30.minutes)
        .build()

    private fun ProviderSeriesId.toMangaBakaId() = MangaBakaSeriesId(this.value.toInt())
}
