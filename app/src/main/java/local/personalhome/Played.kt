package local.personalhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// SmartTube and Spotify have no readable history, but their players publish what plays in a media session.
// MusicListener runs whenever notification access is granted, so it records those plays even while the
// launcher is in the background.

const val SMARTTUBE = "org.smarttube.stable"
const val SPOTIFY = "com.spotify.tv.android"
val VIDEO_APPS = setOf(STREMIO, NUVIO, SMARTTUBE, "com.netflix.ninja", "com.google.android.youtube.tv")
val RECORDED = setOf(SMARTTUBE, SPOTIFY)

private fun store(ctx: Context) = ctx.getSharedPreferences("smarttube", Context.MODE_PRIVATE)
private fun key(pkg: String) = if (pkg == SMARTTUBE) "history" else pkg // SmartTube's list came first

/** Most recent first. */
fun playedHistory(ctx: Context, pkg: String): List<Title> = runCatching { JSONArray(store(ctx).getString(key(pkg), "[]")) }.getOrDefault(JSONArray())
    .let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    .map { e ->
        val id = e.getString("id")
        Title(
            name = e.optString("title"), type = pkg, id = id, videoId = id,
            image = e.optString("art").ifEmpty { if (pkg == SMARTTUBE) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null },
            info = e.optString("channel"), pos = e.optLong("pos"), dur = e.optLong("dur"),
        )
    }

/**
 * Reopens a recorded title in its app. Spotify plays a track from its open.spotify.com link (verified on the
 * TV); its spotify: links and the session's playFromUri didn't start the track.
 */
fun replay(ctx: Context, t: Title) {
    val uri = when {
        t.type == SMARTTUBE -> "https://www.youtube.com/watch?v=${t.id}"
        t.id.startsWith("spotify:") -> "https://open.spotify.com/" + t.id.removePrefix("spotify:").replace(':', '/')
        else -> null
    }
    if (uri == null || !startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(t.type))) launch(ctx, t.type)
}

/** Puts the playing title at the front of its app's history, keeping the last 20. */
fun recordPlay(ctx: Context, c: MediaController) {
    val md = c.metadata ?: return
    val pkg = c.packageName
    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
    val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
    val id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }
        ?: if (pkg == SMARTTUBE || title.isBlank()) return else "$title|$artist"
    val entry = JSONObject().put("id", id).put("title", title).put("channel", artist)
        .put("dur", md.getLong(MediaMetadata.METADATA_KEY_DURATION))
        .put("pos", c.playbackState?.position ?: 0)
    if (pkg != SMARTTUBE) artFile(ctx, id, md)?.let { entry.put("art", it) }
    val old = runCatching { JSONArray(store(ctx).getString(key(pkg), "[]")) }.getOrDefault(JSONArray())
    val next = JSONArray().put(entry)
    for (i in 0 until old.length()) if (next.length() < 20 && old.getJSONObject(i).optString("id") != id) next.put(old.getJSONObject(i))
    store(ctx).edit().putString(key(pkg), next.toString()).apply()
}

/** The cover as a web URL if the session gives one, else its bitmap saved once to a file. */
private fun artFile(ctx: Context, id: String, md: MediaMetadata): String? {
    listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI)
        .firstNotNullOfOrNull { md.getString(it)?.takeIf { u -> u.startsWith("http") } }?.let { return it }
    val f = File(ctx.filesDir, "art/${id.hashCode()}.jpg")
    if (!f.exists()) {
        val b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: return null
        f.parentFile?.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
    return f.toURI().toString()
}

class PlayRecorder(private val ctx: Context) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var watched = emptyList<Pair<MediaController, MediaController.Callback>>()
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }

    fun start() = runCatching {
        msm.addOnActiveSessionsChangedListener(sessions, ComponentName(ctx, MusicListener::class.java), handler)
        watch(msm.getActiveSessions(ComponentName(ctx, MusicListener::class.java)))
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        watch(emptyList())
    }

    private fun watch(list: List<MediaController>) {
        watched.forEach { (c, cb) -> c.unregisterCallback(cb) }
        watched = list.filter { it.packageName in RECORDED }.map { c ->
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = recordPlay(ctx, c)
                override fun onPlaybackStateChanged(state: PlaybackState?) = recordPlay(ctx, c)
            }
            c.registerCallback(cb, handler)
            recordPlay(ctx, c)
            c to cb
        }
    }
}
