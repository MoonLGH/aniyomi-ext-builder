package eu.kanade.tachiyomi.animeextension.en.asianload

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.asianload.extractors.DoodExtractor
import eu.kanade.tachiyomi.animeextension.en.asianload.extractors.FembedExtractor
import eu.kanade.tachiyomi.animeextension.en.asianload.extractors.StreamSBExtractor
import eu.kanade.tachiyomi.animeextension.en.asianload.extractors.StreamTapeExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class AsianLoad : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "AsianLoad"

    override val baseUrl = "https://asianembed.io"

    override val lang = "en"

    override val supportsLatest = false

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Popular Anime

    override fun popularAnimeSelector(): String = "ul.listing.items li a"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/popular?page=$page") // page/$page

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.thumbnail_url = element.select("div.img div.picture img").attr("src")
        anime.title = element.select("div.img div.picture img").attr("alt")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "li.next a"

    // Episodes

    override fun episodeListSelector() = "ul.listing.items.lists li a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))
        episode.name = element.select("div.type span").text() + " Episode: " + element.select("div.name").text().substringAfter("episode-")
        val epNum = element.select("div.name").text().substringAfter("Episode ")
        episode.episode_number = when {
            (epNum.isNotEmpty()) -> epNum.toFloat()
            else -> 1F
        }
        // episode.date_upload = element.select("div.meta span.date").text()
        return episode
    }

    private fun getNumberFromEpsString(epsStr: String): String {
        return epsStr.filter { it.isDigit() }
    }

    // Video urls

    override fun videoListRequest(episode: SEpisode): Request {
        val document = client.newCall(GET(baseUrl + episode.url)).execute().asJsoup()
        val iframe = "https:" + document.select("iframe").attr("src")
        return GET(iframe)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return videosFromElement(document)
    }

    override fun videoListSelector() = "ul.list-server-items li[data-video*=https://sbplay2.com], ul.list-server-items li[data-video*=https://dood], ul.list-server-items li[data-video*=https://streamtape], ul.list-server-items li[data-video*=https://fembed]"

    private fun videosFromElement(document: Document): List<Video> {
        val videoList = mutableListOf<Video>()
        val elements = document.select(videoListSelector())
        for (element in elements) {
            val url = element.attr("data-video")
            Log.i("lol", url)
            val location = element.ownerDocument().location()
            val videoHeaders = Headers.headersOf("Referer", location)
            when {
                url.contains("sbplay2") -> {
                    /*val id = url.substringAfter("e/").substringBefore("?")
                    Log.i("idtest", id)
                    val bytes = id.toByteArray()
                    Log.i("idencode", "$bytes")
                    val bytesToHex = bytesToHex(bytes)
                    Log.i("bytesToHex", bytesToHex)
                    val nheaders = headers.newBuilder()
                        .set("watchsb", "streamsb")
                        .build()
                    val master = "https://sbplay2.com/sourcesx38/7361696b6f757c7c${bytesToHex}7c7c7361696b6f757c7c73747265616d7362/7361696b6f757c7c363136653639366436343663363136653639366436343663376337633631366536393664363436633631366536393664363436633763376336313665363936643634366336313665363936643634366337633763373337343732363536313664373336327c7c7361696b6f757c7c73747265616d7362"
                    Log.i("master", master)

                    val callMaster = client.newCall(GET(master, nheaders)).execute().asJsoup()
                    Log.i("testt", "$callMaster")*/
                    val headers = headers.newBuilder()
                        .set("Referer", url)
                        .set("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:96.0) Gecko/20100101 Firefox/96.0")
                        .set("Accept-Language", "en-US,en;q=0.5")
                        .set("watchsb", "streamsb")
                        .build()
                    val videos = StreamSBExtractor(client).videosFromUrl(url, headers)
                    videoList.addAll(videos)
                }
                url.contains("dood") -> {
                    val video = DoodExtractor(client).videoFromUrl(url)
                    if (video != null) {
                        videoList.add(video)
                    }
                }
                url.contains("fembed") -> {
                    val videos = FembedExtractor().videosFromUrl(url)
                    videoList.addAll(videos)
                }
                url.contains("streamtape") -> {
                    val video = StreamTapeExtractor(client).videoFromUrl(url)
                    if (video != null) {
                        videoList.add(video)
                    }
                }
            }
        }
        return videoList
    }

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", null)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality.contains(quality)) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    // search

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.thumbnail_url = element.select("div.img div.picture img").attr("src")
        anime.title = element.select("div.img div.picture img").attr("alt")
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = "li.next a"

    override fun searchAnimeSelector(): String = "ul.listing.items li a"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search.html?keyword=$query&page=$page"
        } else {
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is TypeList -> {
                        if (filter.state > 0) {
                            val GenreN = getTypeList()[filter.state].query
                            val genreUrl = "$baseUrl/$GenreN?page=$page".toHttpUrlOrNull()!!.newBuilder()
                            return GET(genreUrl.toString(), headers)
                        }
                    }
                }
            }
            throw Exception("Choose Filter")
        }
        return GET(url, headers)
    }

    // Details

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("div.video-details span.date").text()
        anime.description = document.select("div.video-details div.post-entry").text()
        return anime
    }

    // Latest

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SAnime = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    // Filter

    override fun getFilterList() = AnimeFilterList(
        TypeList(typesName),
    )

    private class TypeList(types: Array<String>) : AnimeFilter.Select<String>("Drame Type", types)
    private data class Type(val name: String, val query: String)
    private val typesName = getTypeList().map {
        it.name
    }.toTypedArray()

    private fun getTypeList() = listOf(
        Type("Select", ""),
        Type("Recently Added Sub", ""),
        Type("Recently Added Raw", "recently-added-raw"),
        Type("Drama Movie", "movies"),
        Type("KShow", "kshow"),
        Type("Ongoing Series", "ongoing-series")
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p", "Doodstream", "StreamTape")
            entryValues = arrayOf("1080", "720", "480", "360", "Doodstream", "StreamTape")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }
}
