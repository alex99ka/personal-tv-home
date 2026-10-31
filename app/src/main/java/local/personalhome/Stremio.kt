package local.personalhome

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.DeflaterOutputStream

// ---------- Titles shown in the Home carousel ----------

/**
 * One Continue Watching entry. [videoId] is the episode ("tt123:1:5") for series, the id itself for movies.
 * [type] is Stremio's type, or the package for titles recorded from other apps. [pos]/[dur] in ms.
 */
class Title(
    val name: String, val type: String, val id: String, val videoId: String,
    val image: String?, val info: String, val pos: Long, val dur: Long, val metaAddon: String = "",
) {
    val progress get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

const val STREMIO = "com.stremio.one"
const val NUVIO = "com.nuvio.tv"
private const val API = "https://api.strem.io/api/"
private const val CINEMETA = "https://v3-cinemeta.strem.io"
private const val LINK = "https://link.stremio.com/api/v2"

fun http(url: String, body: String? = null): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000; c.readTimeout = 15_000
    if (body != null) {
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
    }
    return c.inputStream.bufferedReader().use { it.readText() }
}

private fun api(method: String, body: JSONObject): JSONObject {
    val r = JSONObject(http(API + method, body.toString()))
    r.optJSONObject("error")?.let { throw IOException(it.optString("message", "Stremio refused the request")) }
    return r
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map { optString(it) }
private fun JSONObject.str(k: String) = optString(k).takeIf { it.isNotBlank() && it != "null" }

object Stremio {
    /** Link sign-in, as Stremio's own TV app does it: returns the short code and a QR image URL for it. */
    fun linkCreate(): Pair<String, String> =
        JSONObject(http("$LINK/create?type=Create")).getJSONObject("result").let { it.getString("code") to it.getString("qrcode") }

    /** authKey and email once the code was approved on another device, else null. */
    fun linkRead(code: String): Pair<String, String>? {
        val key = JSONObject(http("$LINK/read?type=Read&code=$code")).optJSONObject("result")?.str("authKey") ?: return null
        val email = runCatching { api("getUser", JSONObject().put("type", "GetUser").put("authKey", key)).getJSONObject("result").str("email") }.getOrNull()
        return key to (email ?: "your account")
    }

    fun addons(key: String): List<JSONObject> = api(
        "addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", true),
    ).getJSONObject("result").getJSONArray("addons").objects()

    /** Same rule as Stremio's own row: started, not finished, not removed; newest first, one card per show. */
    suspend fun continueWatching(key: String): List<Title> = withContext(Dispatchers.IO) {
        val addons = runCatching { addons(key) }.getOrDefault(emptyList())
        val items = api(
            "datastoreGet", JSONObject().put("authKey", key).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true),
        ).getJSONArray("result").objects()
            .filter { it.optString("type") != "other" && (!it.optBoolean("removed") || it.optBoolean("temp")) }
            .filter { (it.optJSONObject("state")?.optLong("timeOffset") ?: 0) > 0 }
            .sortedByDescending { it.optJSONObject("state")?.optString("lastWatched") } // ISO dates sort as text
            .distinctBy { videoIdOf(it).substringBefore(':') } // the same show saved under a tmdb: and a tt id
            .take(20)
        coroutineScope { items.map { async { runCatching { describe(it, addons) }.getOrNull() } }.awaitAll().filterNotNull() }
    }

    private fun videoIdOf(item: JSONObject) = item.optJSONObject("state")?.str("video_id") ?: item.getString("_id")

    /**
     * Library item + details from the first add-on that claims this id (the TMDB add-on gives Hebrew
     * names, plots and episode stills), else Cinemeta.
     */
    private fun describe(item: JSONObject, addons: List<JSONObject>): Title {
        val type = item.getString("type")
        val id = item.getString("_id")
        val state = item.optJSONObject("state") ?: JSONObject()
        val videoId = videoIdOf(item)
        val metaAddon = addons.firstOrNull { serves(it.getJSONObject("manifest"), "meta", type, id, explicit = true) }
            ?.getString("transportUrl") ?: "$CINEMETA/manifest.json"
        val meta = runCatching { JSONObject(http("${metaAddon.removeSuffix("/manifest.json")}/meta/$type/${enc(id)}.json")).getJSONObject("meta") }.getOrNull()
        val (season, episode) = videoId.split(':').let { if (it.size == 3) it[1] to it[2] else null to null }
        val ep = meta?.optJSONArray("videos")?.objects()?.let { v ->
            v.firstOrNull { it.optString("id") == videoId } ?: v.firstOrNull { it.optString("season") == season && it.optString("episode") == episode }
        }
        val info = ep?.let { "S${it.optString("season")} E${it.optString("episode")}" } ?: season?.let { "S$it E$episode" }.orEmpty()
        val poster = meta?.str("poster") ?: item.str("poster") ?: meta?.str("background")
        return Title(
            name = item.str("name") ?: meta?.str("name") ?: id, type = type, id = id, videoId = videoId,
            image = poster?.let { tmdb(it, "w342") }, info = info, pos = state.optLong("timeOffset"), dur = state.optLong("duration"),
            metaAddon = metaAddon,
        )
    }

    /** TMDB's "original" images can be 4K; ask for a size that fits a TV. */
    private fun tmdb(url: String, size: String) = url.replace("/t/p/original/", "/t/p/$size/")

    /** [explicit]: only add-ons that list id prefixes, so catch-all add-ons don't answer for meta. */
    private fun serves(m: JSONObject, resource: String, type: String, id: String, explicit: Boolean = false): Boolean =
        (m.optJSONArray("resources") ?: JSONArray()).let { res -> (0 until res.length()).any { i ->
            val r = res.get(i)
            val o = r as? JSONObject
            val prefixes = (o?.optJSONArray("idPrefixes") ?: m.optJSONArray("idPrefixes"))?.strings()
            (o?.optString("name") ?: r.toString()) == resource &&
                type in (o?.optJSONArray("types") ?: m.optJSONArray("types")).strings() &&
                (if (prefixes == null) !explicit else prefixes.any { id.startsWith(it) })
        } }

    /**
     * Asks every stream add-on at once and plays the first stream in the user's add-on order, preferring
     * debrid-cached ones ("[TB+]", "⚡") that start at once. Trailers (ytId), rating cards (externalUrl)
     * and help links without a real file don't count.
     */
    suspend fun playIntent(key: String, t: Title): Intent = withContext(Dispatchers.IO) {
        val detail = Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/${t.type}/${t.id}/${t.videoId}")).setPackage(STREMIO)
        val addons = runCatching { addons(key) }.getOrElse { return@withContext detail }
            .filter { serves(it.getJSONObject("manifest"), "stream", t.type, t.videoId) }
        val found = coroutineScope {
            addons.map { a ->
                async {
                    val base = a.getString("transportUrl").removeSuffix("/manifest.json")
                    runCatching { JSONObject(http("$base/stream/${t.type}/${enc(t.videoId)}.json")).getJSONArray("streams").objects() }
                        .getOrDefault(emptyList()).filter { (it.has("url") || it.has("infoHash")) && "/tag " !in it.optString("title") }
                        .map { a.getString("transportUrl") to it }
                }
            }.awaitAll().flatten()
        }
        val cached = { s: JSONObject -> s.optString("name").let { "+]" in it || "⚡" in it } }
        val pick = found.firstOrNull { cached(it.second) } ?: found.firstOrNull() ?: return@withContext detail
        Intent(Intent.ACTION_VIEW, Uri.parse(playerLink(pick.second.toString(), pick.first, t.metaAddon, t.type, t.id, t.videoId))).setPackage(STREMIO)
    }

    /** Nuvio installs add-ons from stremio:// links, so this copies the account's stream add-ons across. */
    suspend fun copyAddonsToNuvio(ctx: Context, key: String): Int {
        val urls = withContext(Dispatchers.IO) { addons(key) }
            .filter { a -> a.getJSONObject("manifest").optJSONArray("resources")?.toString()?.contains("\"stream\"") == true }
            .map { it.getString("transportUrl") }
            .filter { "://127.0.0.1" !in it } // Stremio's own local-files server
        for (u in urls) {
            startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("stremio://" + u.substringAfter("://"))).setPackage(NUVIO))
            delay(1500) // Nuvio handles one install link at a time
        }
        return urls.size
    }
}

/** Stremio's player deep link: the stream is zlib-deflated JSON in base64, as stremio-core's Stream::encode does. */
fun playerLink(streamJson: String, streamAddon: String, metaAddon: String, type: String, id: String, videoId: String): String {
    val z = ByteArrayOutputStream().also { o -> DeflaterOutputStream(o).use { it.write(streamJson.toByteArray()) } }
    val stream = Base64.getEncoder().encodeToString(z.toByteArray())
    return "stremio:///player/" + listOf(stream, streamAddon, metaAddon, type, id, videoId).joinToString("/") { enc(it) }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

// ---------- Remote images ----------

private val images = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Downloads and decodes [url] no wider than about [maxW] px; cached in memory. */
suspend fun remoteImage(url: String, maxW: Int): ImageBitmap? = images.get("$maxW|$url") ?: withContext(Dispatchers.IO) {
    runCatching {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })!!.asImageBitmap()
    }.getOrNull()?.also { images.put("$maxW|$url", it) }
}

/** A title's backdrop as a scene: sharp for the wallpaper, blurred for the glass. */
suspend fun remoteBackdrop(url: String): Backdrop? {
    val sharp = remoteImage(url, 1920) ?: return null
    return withContext(Dispatchers.Default) { Backdrop(sharp, softBlur(sharp.asAndroidBitmap(), 192, 108)) }
}
