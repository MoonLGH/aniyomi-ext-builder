package eu.kanade.tachiyomi.animeextension.id.animesail

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class AnimeSail : ConfigurableAnimeSource, ParsedAnimeHttpSource() {
    override val baseUrl: String = "https://animesail.com"
    override val lang: String = "id"
    override val name: String = "AnimeSail"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun animeDetailsParse(document: Document): SAnime = getDetail(document)

    private fun getDetail(document: Document): SAnime {
        val anime = SAnime.create()
        val entrycontent = document.select("div.entry-content.serial-info")
        val status = parseStatus(entrycontent.select("table > tbody > tr:nth-child(4) > td").text())
        anime.title = document.select("header > h1").text()
        anime.genre = entrycontent.select("table > tbody > tr:nth-child(3) > td").joinToString(", ") { it.text() }
        anime.status = status
        anime.artist = entrycontent.select("table > tbody > tr:nth-child(5) > td").text()
        anime.author = "UNKNOWN"
        anime.description = "Synopsis: \n" + entrycontent.select("p:nth-child(2)").text()
        anime.thumbnail_url = document.select("div.entry-content.serial-info > img").attr("src")
        return anime
    }

    private fun parseStatus(statusString: String): Int {
        return when (statusString.toLowerCase(Locale.US)) {
            "ongoing" -> SAnime.ONGOING
            "completed" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val epsNum = getNumberFromEpsString(element.select("a").text())
        episode.setUrlWithoutDomain(element.select("a").first().attr("href"))
        episode.episode_number = when {
            (epsNum.isNotEmpty()) -> epsNum.toFloat()
            else -> 1F
        }
        episode.name = element.select("a").text()
        episode.date_upload = System.currentTimeMillis()

        return episode
    }

    private fun getNumberFromEpsString(epsStr: String): String {
        return epsStr.filter { it.isDigit() }
    }

    override fun episodeListSelector(): String = "ul.daftar > li"

    override fun latestUpdatesFromElement(element: Element): SAnime {
        val res = client.newCall(GET(element.select("div > a").attr("href"))).execute().asJsoup()
        val resanime = client.newCall(GET(res.select("div.entry-content > i > a").first().attr("href"))).execute().asJsoup()
        return getDetail(resanime)
    }

    private fun getAnimeFromAnimeElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("div > a").first().attr("href"))
        anime.thumbnail_url = element.select("div > a > div.limit > noscript > img").first().attr("src")
        anime.title = element.select("div > a > div.tt").text()
        return anime
    }
    override fun latestUpdatesNextPageSelector(): String = "div.hpage > a.r"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page")

    override fun latestUpdatesSelector(): String = throw Exception("not used")

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select("div.listupd").first().select("article").map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.select("div > a > div.tt > h2").text()
        anime.thumbnail_url = element.select("img").first().attr("src")
        anime.setUrlWithoutDomain(element.select("div > a").first().attr("href"))
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination > a.next.page-numbers"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/?s=")

    override fun popularAnimeSelector(): String = "div.listupd > article"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.select("div > a > div.tt > h2").text()
        anime.thumbnail_url = element.select("img").first().attr("src")
        anime.setUrlWithoutDomain(element.select("div > a").first().attr("href"))
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = "a.next.page-numbers"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // filter and stuff in v2
        return GET("$baseUrl/page/$page/?s=$query")
    }

    override fun searchAnimeSelector(): String = "div.listupd > article"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val res = client.newCall(GET(document.select("a.singledl").attr("href"))).execute().asJsoup()
        val patternZippy = "div.page > table > tbody > tr > td > a:contains(zippy)"

        val zippy = res.select(patternZippy).mapNotNull {
            runCatching { zippyFromElement(it) }.getOrNull()
        }

        return zippy
    }

    override fun videoFromElement(element: Element): Video = throw Exception("not used")

    private fun zippyFromElement(element: Element): Video {
        val res = client.newCall(GET(element.attr("href"))).execute().asJsoup()
        val scr = res.select("script:containsData(dlbutton)").html()
        var url = element.attr("href").substringBefore("/v/")
        val numbs = scr.substringAfter("\" + (").substringBefore(") + \"")
        val firstString = scr.substringAfter(" = \"").substringBefore("\" + (")
        val num = numbs.substringBefore(" % ").toInt()
        val lastString = scr.substringAfter("913) + \"").substringBefore("\";")
        val nums = num % 51245 + num % 913
        url += firstString + nums.toString() + lastString
        val quality = with(lastString) {
            when {
                contains("1080p") -> "ZippyShare - 1080p"
                contains("720p") -> "ZippyShare - 720p"
                contains("480p") -> "ZippyShare - 480p"
                contains("360p") -> "ZippyShare - 360p"
                else -> "ZippyShare - Unknown Resolution"
            }
        }
        return Video(url, quality, url, null)
    }

    override fun videoListSelector(): String = throw Exception("not used")

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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
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