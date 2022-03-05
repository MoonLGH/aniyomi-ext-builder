package eu.kanade.tachiyomi.animeextension.es.jkhentai

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.es.jkhentai.extractors.StreamTapeExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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

class Jkhentai : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Jkhentai"

    override val baseUrl = "https://www.jkhentai.net"

    override val lang = "es"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularAnimeSelector(): String = "div#contenedor div.items div#directorio div#box_movies div.movie"

    override fun popularAnimeRequest(page: Int): Request = GET("https://www.jkhentai.net/lista/$page")

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(
            element.select("div.imagen a").attr("href")
        )
        anime.title = element.select("h2").text()
        anime.thumbnail_url = element.select("div.imagen img").attr("src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "a.page.larger"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()

        val jsoup = response.asJsoup()
        val animeId = response.request.url.pathSegments.last().replace("-sub-espanol", "").replace("-080p", "-1080p")
        Log.i("bruh", "$animeId")
        jsoup.select("div#contenedor div.items.ptts div#movie div.post div#cssmenu ul li.has-sub.open ul li").forEach { it ->
            Log.i("bruh", "e")

            val epNum = it.select("a").attr("href").replace("https://www.jkhentai.net/ver/$animeId-", "")
            Log.i("bruh", "Episode-$epNum")
            val episode = SEpisode.create().apply {
                episode_number = epNum.toFloat()
                name = "Episodio $epNum"
                url = "/ver/$animeId-$epNum"
                date_upload = System.currentTimeMillis()
            }
            episodes.add(episode)
        }

        return episodes
    }

    override fun episodeListSelector() = throw Exception("not used")

    override fun episodeFromElement(element: Element) = throw Exception("not used")

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        document.select("div#contenedor div.items.ptts div#movie div.post div#player-container ul.player-menu li").forEach { it ->
            val server = it.select("a").text()
            Log.i("bruh", "1Server:$server")
            document.select("div#contenedor div.items.ptts div#movie div.post div#player-container div.play-c").forEach() {
                if (server == "StreamTape") {
                    val url = it.select("div.player-content iframe").attr("src")
                    val video = StreamTapeExtractor(client).videoFromUrl(url, server)
                    if (video != null) {
                        videoList.add(video)
                    }
                }
            }
        }
        return videoList
    }

    override fun videoListSelector() = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "StreamTape")
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality == quality) {
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

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as GenreFilter

        return when {
            query.isNotBlank() -> GET("$baseUrl/buscador.php?search=$query&page=$page", headers)
            genreFilter.state != 0 -> GET("$baseUrl/genero/${genreFilter.toUriPart()}/$page")
            else -> GET("$baseUrl/lista/$page ")
        }
    }
    override fun searchAnimeFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.thumbnail_url = document.selectFirst("div#contenedor div.items.ptts div#movie div.post div.headingder div.datos div.imgs.tsll a img").attr("src")
        anime.title = document.selectFirst("div#contenedor div.items.ptts div#movie div.post div.headingder div.datos div.dataplus h1").text()
        anime.description = "Titulo Original: " + document.select("div#contenedor div.items.ptts div#movie div.post div.headingder div.datos div.dataplus span.original").first().ownText()
        anime.genre = document.select("div.items.ptts div#movie div.post div.headingder div.datos div.dataplus div#dato-1.data-content div.xmll p.xcsd strong a").joinToString { it.text() }
        anime.status = SAnime.COMPLETED
        return anime
    }

    override fun latestUpdatesNextPageSelector() = throw Exception("not used")

    override fun latestUpdatesFromElement(element: Element) = throw Exception("not used")

    override fun latestUpdatesRequest(page: Int) = throw Exception("not used")

    override fun latestUpdatesSelector() = throw Exception("not used")

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("La busqueda por texto ignora el filtro"),
        GenreFilter()
    )

    private class GenreFilter : UriPartFilter(
        "Generos",
        arrayOf(
            Pair("<selecionar>", ""),
            Pair("Acción", "accion"),
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("Bakunyuu", "bakunyuu"),
            Pair("Bondage", "bondage"),
            Pair("Comedia", "comedia"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Escolares", "escolares"),
            Pair("Fantasía", "fantasía"),
            Pair("Enfermeras", "enfermeras"),
            Pair("Futanari", "futanari"),
            Pair("Harem", "Harem"),
            Pair("Incesto", "incesto"),
            Pair("Juegos Sexuales", "juegos-sexuales"),
            Pair("Lolicon", "lolicon"),
            Pair("Maduras", "milf"),
            Pair("Notorare", "netorare"),
            Pair("Ninfomania", "ninfomania"),
            Pair("Orgia", "orgia"),
            Pair("Romance", "romance"),
            Pair("Sin censura", "sin-censura"),
            Pair("Sumisas", "sumisas"),
            Pair("Sirvientas", "sirvientas"),
            Pair("Teacher", "teacher"),
            Pair("Tentaculos", "tentaculos"),
            Pair("TETONAS", "tetonas"),
            Pair("Violacion", "violacion"),
            Pair("Virgenes(como tu)", "virgenes"),
            Pair("Yaoi", "Yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Hentai 3D", "hentai-3d"),
            Pair("Bestialidad", "bestialidad"),
            Pair("Chikan", "chikan"),
            Pair("Casadas", "casadas"),
            Pair("Cream Pie", "cream-pie"),
            Pair("Gangbang", "gangbang"),
            Pair("Hardcore", "hardcore"),
            Pair("Maids", "maids"),
            Pair("Netorase", "netorase"),
            Pair("Shota", "shota"),
            Pair("Succubus", "succubus")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("StreamTape")
            entryValues = arrayOf("StreamTape")
            setDefaultValue("StreamTape")
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
