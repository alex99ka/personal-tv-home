package local.personalhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.net.URL

// ---------- Apps ----------

class App(val pkg: String, val label: String, val art: ImageBitmap, val isBanner: Boolean, val bg: Color, val installed: Long)

private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(b))
    return b
}

/** Every launchable app, banner art preferred. [cache] is keyed by package + update time so resumes are cheap. */
fun loadApps(ctx: Context, cache: MutableMap<String, App>): List<App> = synchronized(cache) {
    val pm = ctx.packageManager
    val found = LinkedHashMap<String, android.content.pm.ActivityInfo>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER))
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0))
            found.putIfAbsent(ri.activityInfo.packageName, ri.activityInfo)
    found.remove(ctx.packageName)
    found.values.mapNotNull { ai ->
        val pi = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull() ?: return@mapNotNull null
        cache.getOrPut("${ai.packageName}:${pi.lastUpdateTime}") {
            val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
            if (banner != null) {
                App(ai.packageName, ai.loadLabel(pm).toString(), banner.toBitmap(320, 180).asImageBitmap(), true, Color.Black, pi.firstInstallTime)
            } else {
                val icon = ai.loadIcon(pm).toBitmap(192, 192)
                val avg = Bitmap.createScaledBitmap(icon, 1, 1, true).getPixel(0, 0)
                val bg = Color(avg).copy(alpha = 1f).let { Color(it.red * .55f, it.green * .55f, it.blue * .55f) }
                App(ai.packageName, ai.loadLabel(pm).toString(), icon.asImageBitmap(), false, bg, pi.firstInstallTime)
            }
        }
    }.sortedBy { it.label.lowercase() }
}

fun launch(ctx: Context, pkg: String): Boolean {
    val pm = ctx.packageManager
    val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
    return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

fun startSafely(ctx: Context, vararg intents: Intent): Boolean =
    intents.any { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }

/** Apps that usually live on this household's TVs; the first run builds the dock from whichever are installed. */
val PREFERRED = listOf(
    "com.stremio.one", "org.smarttube.stable", "com.nuvio.tv", "com.netflix.ninja",
    "com.spotify.tv.android", "com.google.android.youtube.tv", "com.disney.disneyplus", "com.android.vending",
)

// ---------- Preferences ----------

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val cache = mutableStateMapOf<String, String>() // reading it makes Compose recompose on set()

    private fun get(k: String, def: String) = cache[k] ?: sp.getString(k, null) ?: def
    private fun put(k: String, v: String) { cache[k] = v; sp.edit().putString(k, v).apply() }
    private fun list(k: String) = get(k, "").split(',').filter { it.isNotEmpty() }

    val hasFavorites get() = sp.contains("fav")
    var favorites: List<String> get() = list("fav"); set(v) = put("fav", v.joinToString(","))
    var recents: List<String> get() = list("recent"); set(v) = put("recent", v.joinToString(","))
    var wallpaper: String get() = get("wp", WALLPAPERS[0].first); set(v) = put("wp", v)
    var clock24: Boolean get() = get("clock24", "1") == "1"; set(v) = put("clock24", if (v) "1" else "0")
    var weatherOn: Boolean get() = get("weather", "1") == "1"; set(v) = put("weather", if (v) "1" else "0")
    var fahrenheit: Boolean get() = get("fahr", "0") == "1"; set(v) = put("fahr", if (v) "1" else "0")
    var focusName: Boolean get() = get("focusName", "1") == "1"; set(v) = put("focusName", if (v) "1" else "0")
    var shade: Int get() = get("shade", "1").toInt(); set(v) = put("shade", v.toString())
    var stremioKey: String get() = get("stremioKey", ""); set(v) = put("stremioKey", v)
    var stremioEmail: String get() = get("stremioEmail", ""); set(v) = put("stremioEmail", v)
    var album: String get() = get("album", ""); set(v) = put("album", v)
}

val WALLPAPERS = listOf(
    "wp_photo1" to R.drawable.wp_photo1, "wp_photo3" to R.drawable.wp_photo3,
    "wp_photo5" to R.drawable.wp_photo5, "wp_photo4" to R.drawable.wp_photo4,
    "wp_photo2" to R.drawable.wp_photo2, "wp_photo6" to R.drawable.wp_photo6,
    "wp_scene_aurora" to R.drawable.wp_scene_aurora, "wp_scene_moon_lake" to R.drawable.wp_scene_moon_lake,
    "wp_scene_mirror_lake" to R.drawable.wp_scene_mirror_lake, "wp_scene_city_lights" to R.drawable.wp_scene_city_lights,
)

class Backdrop(val sharp: ImageBitmap, val blur: ImageBitmap)

/** Wallpaper choices besides a single built-in photo; both change photo every minute. */
const val WP_ALBUM = "album"
const val WP_STOCK = "stock"

/**
 * Photos of a shared Google Photos album (Share > Create link), sized for the TV.
 * shortcut: scrapes the public album page, which lists its photos inline; switch to the Photos Picker API if Google changes the page.
 */
fun albumPhotos(link: String): List<String> =
    Regex("""https://lh3\.googleusercontent\.com/pw/[\w-]+""").findAll(http(link)).map { it.value + "=w1920-h1080" }.distinct().toList()

fun loadWallpaper(res: Resources, key: String): Backdrop {
    val id = (WALLPAPERS.firstOrNull { it.first == key } ?: WALLPAPERS[0]).second
    val sharp = BitmapFactory.decodeResource(res, id)
    val small = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inSampleSize = 8 })
    return Backdrop(sharp.asImageBitmap(), softBlur(small, 192, 108))
}

fun thumbnail(res: Resources, id: Int): ImageBitmap =
    BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inSampleSize = 4 }).asImageBitmap()

// ---------- Blur ----------
// A tiny, heavily blurred copy is scaled up by the GPU when drawn; looks like frosted glass on any API level
// and costs nothing per frame, unlike RenderEffect (API 31+, every frame).

fun softBlur(src: Bitmap, w: Int, h: Int): ImageBitmap {
    val srcRatio = src.width.toFloat() / src.height
    val ratio = w.toFloat() / h
    val cw = if (srcRatio > ratio) (src.height * ratio).toInt() else src.width
    val ch = if (srcRatio > ratio) src.height else (src.width / ratio).toInt()
    val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
    val small = Bitmap.createScaledBitmap(cropped, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    repeat(3) { boxBlur(px, w, h, 3) }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** One horizontal + one vertical box-blur pass, in place, edge pixels clamped. */
fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(px.size)
    fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, step: Int, lineStep: Int) {
        val n = 2 * r + 1
        for (l in 0 until lines) for (i in 0 until len) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (k in -r..r) {
                val c = src[l * lineStep + (i + k).coerceIn(0, len - 1) * step]
                a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
            }
            dst[l * lineStep + i * step] = (a / n shl 24) or (rr / n shl 16) or (g / n shl 8) or (b / n)
        }
    }
    pass(px, tmp, w, h, 1, w)
    pass(tmp, px, h, w, w, 1)
}

// ---------- Weather (wttr.in: no key, locates by IP) ----------

enum class Sky { Clear, PartCloud, Cloud, Fog, Rain, Snow, Storm }

class Weather(val temp: String, val desc: String, val sky: Sky)

private val SNOW = setOf(179, 182, 185, 227, 230, 317, 320, 323, 326, 329, 332, 335, 338, 350, 362, 365, 368, 371, 374, 377)

fun skyOf(code: Int): Sky = when (code) {
    113 -> Sky.Clear
    116 -> Sky.PartCloud
    119, 122 -> Sky.Cloud
    143, 248, 260 -> Sky.Fog
    200, 386, 389, 392, 395 -> Sky.Storm
    in SNOW -> Sky.Snow
    else -> Sky.Rain
}

fun fetchWeather(fahrenheit: Boolean): Weather? = runCatching {
    val conn = URL("https://wttr.in/?format=j1").openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
    val c = JSONObject(conn.getInputStream().bufferedReader().use { it.readText() })
        .getJSONArray("current_condition").getJSONObject(0)
    Weather(
        c.getString(if (fahrenheit) "temp_F" else "temp_C") + "°",
        c.getJSONArray("weatherDesc").getJSONObject(0).getString("value").trim(),
        skyOf(c.getString("weatherCode").toInt()),
    )
}.getOrNull()

// ---------- Music (active media sessions) ----------

/** Granting it notification access is what lets us read media sessions; while connected it also records SmartTube and Spotify plays. */
class MusicListener : NotificationListenerService() {
    private var recorder: PlayRecorder? = null
    override fun onListenerConnected() { recorder = PlayRecorder(this).also { it.start() } }
    override fun onListenerDisconnected() { recorder?.stop(); recorder = null }
}

class NowPlaying(
    val title: String, val artist: String, val art: ImageBitmap?, val artBlur: ImageBitmap?,
    val state: PlaybackState?, val duration: Long, val controller: MediaController,
) {
    val playing get() = state?.state == PlaybackState.STATE_PLAYING
    /** Callers re-read this on a timer; the session stamps its updates with elapsedRealtime, not wall time. */
    fun position(): Long {
        val s = state ?: return 0
        val p = if (playing) s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() else s.position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }
}

class Music(ctx: Context) {
    var now by mutableStateOf<NowPlaying?>(null); private set
    var granted by mutableStateOf(false); private set

    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val cn = ComponentName(ctx, MusicListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers = emptyList<MediaController>()
    private var lastPlaying = 0L
    private var artSrc: Bitmap? = null
    private var artBlur: ImageBitmap? = null
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        granted = try {
            msm.addOnActiveSessionsChangedListener(sessions, cn, handler)
            watch(msm.getActiveSessions(cn)); true
        } catch (_: SecurityException) { false }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        watch(emptyList())
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, handler) }
        refresh()
    }

    private fun refresh() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val md = c?.metadata
        val t = System.currentTimeMillis()
        if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) lastPlaying = t
        // Paused music stays on Home for a while so the player doesn't vanish the moment you pause it.
        if (c == null || md == null || t - lastPlaying > 10 * 60_000) { now = null; return }
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== artSrc) { artSrc = art; artBlur = art?.let { softBlur(it, 96, 54) } }
        now = NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            art = art?.asImageBitmap(), artBlur = artBlur, state = c.playbackState,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION), controller = c,
        )
    }
}

