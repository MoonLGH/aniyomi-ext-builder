package eu.kanade.tachiyomi.animeextension.ar.animerco.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient

class VidBomExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String): List<Video> {
        val doc = client.newCall(GET(url)).execute().asJsoup()
        val script = doc.selectFirst("script:containsData(sources)")
        Log.i("looool", "$script")
        val data = script.data().substringAfter("sources: [").substringBefore("],")
        Log.i("loool", "$data")
        val sources = data.split("file:\"").drop(1)
        val videoList = mutableListOf<Video>()
        for (source in sources) {
            val src = source.substringBefore("\"")
            Log.i("looo", src)
            val quality = "Vidbom:" + source.substringAfter("label:\"").substringBefore("\"") // .substringAfter("format: '")
            val video = Video(src, quality, src, null)
            videoList.add(video)
        }
        return videoList
        /*Log.i("looool", "$js")
        val json = JSONObject(js)
        Log.i("looool", "$json")
        val videoList = mutableListOf<Video>()
        val jsonArray = json.getJSONArray("sources")
        for (i in 0 until jsonArray.length()) {
            val `object` = jsonArray.getJSONObject(i)
            val videoUrl = `object`.getString("file")
            Log.i("looool", videoUrl)
            val quality = "Vidbom:" + `object`.getString("label")
            videoList.add(Video(videoUrl, quality, videoUrl, null))
        }
        return videoList*/
        /*if (jas.contains("sources")) {
            val js = script.data()
            val json = JSONObject(js)
            val videoList = mutableListOf<Video>()
            val jsonArray = json.getJSONArray("sources")
            for (i in 0 until jsonArray.length()) {
                val `object` = jsonArray.getJSONObject(i)
                val videoUrl = `object`.getString("file")
                Log.i("lol", videoUrl)
                val quality = "Vidbom:" + `object`.getString("label")
                videoList.add(Video(videoUrl, quality, videoUrl, null))
            }
            return videoList
        } else {
            val videoList = mutableListOf<Video>()
            videoList.add(Video(url, "no 2video", null, null))
            return videoList
        }*/
    }
}
