package local.personalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

enum class Screen { Home, Discover, MySpace, Apps, Search, Wallpapers, Appearance, Stremio }

class MainActivity : ComponentActivity() {
    lateinit var prefs: Prefs
    lateinit var music: Music
    var apps by mutableStateOf(emptyList<App>())
    var screen by mutableStateOf(Screen.Home)
    var panel by mutableStateOf(false)
    var weather by mutableStateOf<Weather?>(null)
    var dockIndex by mutableIntStateOf(0) // remembered so returning Home lands where you left
    var rows by mutableStateOf(emptyMap<String, List<Title>>()) // app package -> its carousel
    var menu by mutableStateOf<App?>(null) // long-pressed app
    var menuReturn: FocusRequester? = null // where focus goes back when the menu closes
    var listen by mutableStateOf(false) // full-screen music view
    var listenReturn = false // set while leaving it, so focus goes back to its Listen pill
    var homePress by mutableIntStateOf(0) // the Home button always lands on the dock
    var status by mutableStateOf<String?>(null)
    private val artCache = HashMap<String, App>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        music = Music(this)
        setContent { Root(this) }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { loadApps(this@MainActivity, artCache) }
            if (!prefs.hasFavorites && apps.isNotEmpty()) {
                val have = apps.map { it.pkg }.toSet()
                prefs.favorites = (PREFERRED.filter { it in have } + apps.map { it.pkg }).distinct().take(6)
            }
        }
        refreshRows() // every return Home, so what you just watched moves to the front
    }

    fun refreshRows() {
        val key = prefs.stremioKey
        rows = rows.filterKeys { (it == STREMIO || it == NUVIO) && key.isNotEmpty() } + RECORDED.associateWith { playedHistory(this, it) }
        if (key.isNotEmpty()) lifecycleScope.launch { runCatching { Stremio.continueWatching(key) }.onSuccess { rows = rows + (STREMIO to it) + (NUVIO to it) } }
    }

    /** [pkg] is the dock app whose row it is: Nuvio shows Stremio's list but can't be sent to a title, so it just opens. */
    fun play(pkg: String, t: Title) {
        if (pkg in VIDEO_APPS) pauseMusic()
        if (pkg == NUVIO) { open(NUVIO); return }
        if (t.type in RECORDED) { replay(this, t); return }
        if (status != null) return
        status = "Finding a stream…"
        lifecycleScope.launch {
            val i = Stremio.playIntent(prefs.stremioKey, t)
            status = null
            startSafely(this@MainActivity, i)
        }
    }

    override fun onStart() { super.onStart(); music.start() }
    override fun onStop() { music.stop(); super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) { panel = false; menu = null; listen = false; screen = Screen.Home; homePress++ }
    }

    fun open(pkg: String) {
        if (pkg in VIDEO_APPS) pauseMusic()
        if (launch(this, pkg)) prefs.recents = (listOf(pkg) + prefs.recents.filter { it != pkg }).take(12)
    }

    /** Video apps here don't take audio focus, so Spotify would keep playing under them. */
    private fun pauseMusic() { music.now?.takeIf { it.playing }?.controller?.transportControls?.pause() }

    /** The remote's settings button (it sends NOTIFICATION) opened the stock launcher's panel; here it opens ours. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_NOTIFICATION) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) { menu = null; listen = false; screen = Screen.Home; panel = !panel }
        return true
    }

    fun toggleFavorite(pkg: String) {
        val f = prefs.favorites
        prefs.favorites = if (pkg in f) f - pkg else f + pkg
    }

    fun go(s: Screen) { panel = false; screen = s }

    fun showMenu(app: App, back: FocusRequester? = null) { menuReturn = back; menu = app }
}

// ---------- Root ----------

@Composable
fun Root(a: MainActivity) {
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val wallpaper by produceState<Backdrop?>(null, a.prefs.wallpaper, a.prefs.album) {
        val wp = a.prefs.wallpaper
        val builtIn = { key: String -> suspend { withContext(Dispatchers.Default) { loadWallpaper(a.resources, key) } } }
        if (wp != WP_ALBUM && wp != WP_STOCK) { value = builtIn(wp)(); return@produceState }
        while (true) { // the album is re-read each pass so new photos show up; stock photos if it can't be read
            val album = if (wp == WP_ALBUM) runCatching { withContext(Dispatchers.IO) { albumPhotos(a.prefs.album) } }.getOrDefault(emptyList()) else emptyList()
            val loads = album.map { suspend { remoteBackdrop(it) } }.ifEmpty { WALLPAPERS.map { builtIn(it.first) } }
            for (load in loads.shuffled()) { value = load() ?: continue; delay(60_000) }
            delay(5_000) // a pass where nothing loaded doesn't spin
        }
    }
    val now = a.music.now
    val art = now?.artBlur
    // Music takes over the whole scene: its blurred album art replaces the wallpaper.
    val scene: Backdrop? = if (art != null) Backdrop(art, art) else wallpaper

    LaunchedEffect(a.prefs.weatherOn, a.prefs.fahrenheit) {
        if (!a.prefs.weatherOn) return@LaunchedEffect
        while (true) {
            a.weather = withContext(Dispatchers.IO) { fetchWeather(a.prefs.fahrenheit) } ?: a.weather
            delay(if (a.weather == null) 120_000 else 30 * 60_000L)
        }
    }

    BackHandler(a.panel || a.screen != Screen.Home) { if (a.panel) a.panel = false else a.screen = Screen.Home }

    CompositionLocalProvider(LocalBackdrop provides scene?.blur, LocalRootSize provides rootSize) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12)).onSizeChanged { rootSize = it }) {
            Crossfade(scene, animationSpec = tween(700), label = "scene") { s ->
                if (s != null) Image(
                    s.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    filterQuality = if (s.sharp === s.blur) FilterQuality.Low else FilterQuality.High,
                )
            }
            if (art != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f))) // keep the artwork's colour
            Shade(a.prefs.shade)

            AnimatedContent(
                a.screen,
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(380, easing = Settle), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
                },
                label = "screen",
            ) { s ->
                Box(Modifier.fillMaxSize()) {
                    when (s) {
                        Screen.Home -> Home(a)
                        Screen.Apps -> AppsScreen(a)
                        Screen.MySpace -> MySpaceScreen(a)
                        Screen.Discover -> DiscoverScreen(a)
                        Screen.Search -> SearchScreen(a)
                        Screen.Wallpapers -> WallpaperScreen(a)
                        Screen.Appearance -> AppearanceScreen(a)
                        Screen.Stremio -> StremioScreen(a)
                    }
                }
            }

            val scrim by animateFloatAsState(if (a.panel) 0.35f else 0f, tween(300), label = "scrim")
            if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
            AnimatedVisibility(
                a.panel, Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(380, easing = Settle)) { it / 2 } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(180)),
            ) { SettingsPanel(a) }
            a.menu?.let { AppMenu(a, it) }
        }
    }
    LaunchedEffect(a.menu) {
        val back = a.menuReturn?.takeIf { a.menu == null } ?: return@LaunchedEffect
        a.menuReturn = null
        repeat(10) { if (runCatching { back.requestFocus() }.isSuccess) return@LaunchedEffect; delay(30) }
    }
}

/** Light shading so white text stays readable on any photo, without a muddy band across the bottom. */
@Composable
fun Shade(level: Int) {
    val k = listOf(0.7f, 1f, 1.4f)[level.coerceIn(0, 2)]
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.34f * k), 0.28f to Color.Transparent,
                0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f * k),
            )
        )
    )
}

// ---------- Home ----------

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Home(a: MainActivity) {
    val playing = a.music.now != null
    LaunchedEffect(playing) { if (!playing) { a.listen = false; a.listenReturn = false } }
    SharedTransitionLayout {
        AnimatedContent(
            a.listen && playing,
            transitionSpec = { fadeIn(tween(380, easing = Settle)) togetherWith fadeOut(tween(300, easing = Settle)) },
            label = "listen",
        ) { open ->
            val s = Shared(this@SharedTransitionLayout, this)
            val n = a.music.now
            if (open && n != null) ListenView(a, n, s) else HomeMain(a, s)
        }
    }
}

@Composable
private fun HomeMain(a: MainActivity, s: Shared) {
    val favorites = a.prefs.favorites.mapNotNull { p -> a.apps.firstOrNull { it.pkg == p } }
    var focusedName by remember { mutableStateOf<String?>(null) }
    val music = a.music.now
    val dockFocus = remember { FocusRequester() }
    val pkg = favorites.getOrNull(a.dockIndex)?.pkg
    val row = pkg?.let { a.rows[it] }?.takeIf { it.isNotEmpty() }
    // Music shows above the dock unless another app with its own row is focused.
    val above = when {
        music != null && (row == null || pkg == music.controller.packageName) -> "music"
        row != null -> "row:$pkg"
        else -> ""
    }
    Box(Modifier.fillMaxSize()) {
        Header(a, Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                above,
                transitionSpec = { fadeIn(tween(300, delayMillis = 60, easing = Settle)) togetherWith fadeOut(tween(180)) },
                contentAlignment = Alignment.BottomCenter,
                label = "above",
            ) { k ->
                val n = a.music.now
                val titles = a.rows[k.removePrefix("row:")]?.takeIf { it.isNotEmpty() }
                when {
                    k == "music" && n != null -> MusicDeck(a, n, s)
                    k.startsWith("row:") && titles != null -> Carousel(a, k.removePrefix("row:"), titles, dockFocus)
                    else -> {
                        val name = focusedName.takeIf { a.prefs.focusName }
                        Crossfade(name, animationSpec = tween(220), label = "name") { nm ->
                            Txt(nm ?: "", Type.heading, Modifier.padding(bottom = 14.dp), color = Dim)
                        }
                    }
                }
            }
            Dock(a, favorites, dockFocus) { focusedName = it }
        }
    }
}

/**
 * The focused dock app's titles as a cover flow: the selected card in front, the rest turned away behind it.
 * Up from the dock enters it, Left/Right flip, OK plays, Down or Back returns to the app.
 */
@Composable
fun Carousel(a: MainActivity, pkg: String, titles: List<Title>, dockFocus: FocusRequester) {
    var sel by remember(titles) { mutableIntStateOf(0) }
    var inside by remember { mutableStateOf(false) }
    val at by animateFloatAsState(sel.toFloat(), soft(), label = "flip")
    val t = titles[sel.coerceIn(0, titles.size - 1)]
    BackHandler(inside) { dockFocus.requestFocus() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(t.name, Type.title, Modifier.padding(horizontal = 240.dp))
        a.status?.let { Txt(it, Type.label, Modifier.padding(top = 2.dp), color = Dim) }
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)
                .onFocusChanged { inside = it.isFocused }
                .onKeyEvent { e ->
                    val down = e.type == KeyEventType.KeyDown
                    when (e.key) {
                        Key.DirectionLeft -> { if (down) sel = (sel - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { if (down) sel = (sel + 1).coerceAtMost(titles.size - 1); true }
                        Key.DirectionDown -> { if (down) dockFocus.requestFocus(); true }
                        else -> if (e.isOk) { if (!down) a.play(pkg, titles[sel]); true } else false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            titles.forEachIndexed { i, title ->
                val d = i - at
                if (abs(d) < 4.5f) key(title.videoId) { PosterCard(title, d, inside && i == sel) }
            }
        }
    }
}


/** A playing-card shaped cover with the watch position beneath it, placed by its distance [d] from the front. */
@Composable
fun PosterCard(t: Title, d: Float, front: Boolean) {
    val img by produceState<ImageBitmap?>(null, t.image) { value = t.image?.let { remoteImage(it, 360) } }
    val ad = abs(d)
    val shape = RoundedCornerShape(16.dp)
    // Film posters 2:3, YouTube frames 16:9, album covers square; spacing grows with the card.
    val (w, ratio) = when (t.type) { SMARTTUBE -> 300 to 16 / 9f; SPOTIFY -> 190 to 1f; else -> 140 to 2 / 3f }
    val k = w / 140f
    Glass(
        Modifier.zIndex(-ad).graphicsLayer {
            translationX = sign(d) * k * (ad.coerceAtMost(1f) * 138.dp.toPx() + (ad - 1f).coerceAtLeast(0f) * 86.dp.toPx())
            val s = 1f - 0.15f * ad.coerceAtMost(3f)
            scaleX = s; scaleY = s
            rotationY = -d.coerceIn(-1f, 1f) * 40f
            cameraDistance = 14f * density
            alpha = (1.15f - 0.28f * ad).coerceIn(0f, 1f)
        }
            .width(w.dp).then(if (front) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape) else Modifier),
        corner = 16.dp, tint = 0.14f,
    ) { Column {
        // Cover inset in a glass frame, with the time and a small Glass Current beneath it.
        Box(Modifier.padding(6.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.08f))) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
            if (t.info.isNotEmpty()) Txt( // season/episode, or the channel/artist for recorded apps
                t.info, Type.caption.copy(letterSpacing = 0.4.sp),
                Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
            when {
                t.dur >= 3_600_000 -> Txt("${fmtTime(t.pos)}/${fmtTime(t.dur)}", Type.tnum.copy(fontSize = 11.sp), color = Dim) // fits the card
                t.dur > 0 -> Txt("${fmtTime(t.pos)} / ${fmtTime(t.dur)}", Type.tnum, color = Dim)
                else -> Txt(t.name, Type.tnum, color = Dim)
            }
            Canvas(Modifier.fillMaxWidth().height(14.dp)) { drawCurrent(t.progress, 0.6f, amp = 3f * density, stroke = 2.4f * density) }
        }
    } }
}

@Composable
fun Header(a: MainActivity, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        val w = a.weather
        if (a.prefs.weatherOn && w != null) {
            Ico(skyIcon(w.sky), 26.dp, Dim)
            Spacer(Modifier.width(10.dp))
            Txt(w.temp, Type.heading.copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(10.dp))
            Txt(w.desc, Type.body, color = Faint)
        }
        Spacer(Modifier.weight(1f))
        Clock(a.prefs.clock24)
        Spacer(Modifier.width(22.dp))
        GlassButton(Icons.Rounded.Settings, 52.dp) { a.panel = true }
    }
}

fun skyIcon(s: Sky): ImageVector {
    val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it < 6 || it >= 19 }
    return when (s) {
        Sky.Clear -> if (night) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny
        Sky.PartCloud -> if (night) Icons.Rounded.NightsStay else Icons.Rounded.WbCloudy
        Sky.Cloud -> Icons.Rounded.Cloud
        Sky.Fog -> Icons.Rounded.Dehaze
        Sky.Rain -> Icons.Rounded.WaterDrop
        Sky.Snow -> Icons.Rounded.AcUnit
        Sky.Storm -> Icons.Rounded.Bolt
    }
}

/** Circular glass button: clear glass at rest, inverts to soft white with dark glyph on focus. */
@Composable
fun GlassButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Glass(modifier.size(size), corner = size / 2, shadow = false) {
        Focusable(onClick, Modifier.fillMaxSize(), corner = size / 2, scale = 1.06f, fill = true) { f ->
            Ico(icon, size * 0.46f, if (f) OnLight else Ink)
        }
    }
}

const val TILE_W = 144
const val TILE_H = 84

@Composable
fun Dock(a: MainActivity, favorites: List<App>, first: FocusRequester, onFocusName: (String?) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = a.dockIndex.coerceAtMost(favorites.size))
    val target = a.dockIndex.coerceIn(0, favorites.size) // favorites.size = the All Apps control
    LaunchedEffect(favorites.isNotEmpty(), a.panel, a.menu == null, a.homePress) {
        if (a.listenReturn) { a.listenReturn = false; return@LaunchedEffect } // the player puts focus back on Listen
        if (a.panel || a.menu != null || favorites.isEmpty()) return@LaunchedEffect // apps not loaded yet
        list.scrollToItem(target)
        repeat(20) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(50) }
    }
    Glass(Modifier.padding(horizontal = 72.dp), corner = 32.dp) {
        LazyRow(
            state = list,
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(favorites, key = { _, app -> app.pkg }) { i, app ->
                Focusable(
                    onClick = { a.open(app.pkg) },
                    modifier = Modifier.size(TILE_W.dp, TILE_H.dp).then(if (i == target) Modifier.focusRequester(first) else Modifier),
                    onLongClick = { a.showMenu(app) },
                    onFocus = { if (it) { a.dockIndex = i; onFocusName(app.label) } else onFocusName(null) },
                ) { AppArt(app) }
            }
            item(key = "all") {
                Focusable(
                    onClick = { a.go(Screen.Apps) },
                    modifier = Modifier.size(TILE_H.dp, TILE_H.dp).then(if (target == favorites.size) Modifier.focusRequester(first) else Modifier),
                    onFocus = { if (it) { a.dockIndex = favorites.size; onFocusName("All Apps") } else onFocusName(null) },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Ico(Icons.Rounded.Apps, 30.dp)
                    }
                }
            }
        }
    }
}

// ---------- Settings panel ----------

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsPanel(a: MainActivity) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(30) } }
    Glass(Modifier.padding(20.dp).width(400.dp).fillMaxHeight(), corner = 32.dp, tint = 0.14f) {
        Column(
            // Back would otherwise only move focus out of the group (Compose maps it to a focus exit).
            Modifier.padding(horizontal = 16.dp, vertical = 22.dp)
                .onPreviewKeyEvent { e -> (e.key == Key.Back).also { if (it && e.type == KeyEventType.KeyUp) a.panel = false } }
                .verticalScroll(rememberScrollState()).focusGroup()
                .focusProperties { onExit = { cancelFocusChange() } },
        ) {
            Txt("Settings", Type.title, Modifier.padding(start = 14.dp, bottom = 10.dp))
            PanelRow(Icons.Rounded.Cast, "Chromecast settings", "Network, remotes, display, sound", Modifier.focusRequester(first)) {
                a.panel = false
                startSafely(a, Intent(Settings.ACTION_SETTINGS))
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            PanelRow(Icons.Rounded.Home, "Home") { a.go(Screen.Home) }
            PanelRow(Icons.Rounded.Explore, "Discover") { a.go(Screen.Discover) }
            PanelRow(Icons.Rounded.Bookmark, "My Space") { a.go(Screen.MySpace) }
            PanelRow(Icons.Rounded.Apps, "Apps") { a.go(Screen.Apps) }
            PanelRow(Icons.Rounded.Search, "Search") { a.go(Screen.Search) }
            PanelRow(Icons.Rounded.Wallpaper, "Wallpapers") { a.go(Screen.Wallpapers) }
            PanelRow(Icons.Rounded.Palette, "Appearance") { a.go(Screen.Appearance) }
            PanelRow(Icons.Rounded.Movie, "Stremio account", "Continue Watching on Home") { a.go(Screen.Stremio) }
        }
    }
}

/** Hold OK on an app: open it, its system info page, or add/remove it from the bar. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppMenu(a: MainActivity, app: App) {
    val first = rememberInitialFocus()
    BackHandler { a.menu = null }
    val pinned = app.pkg in a.prefs.favorites
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Glass(Modifier.width(420.dp), corner = 28.dp, tint = 0.14f) {
            Column(
                // Back would otherwise only move focus out of the group (Compose maps it to a focus exit).
                Modifier.padding(16.dp).ignoreHeldOk()
                    .onPreviewKeyEvent { e -> (e.key == Key.Back).also { if (it && e.type == KeyEventType.KeyUp) a.menu = null } }
                    .focusGroup().focusProperties { onExit = { cancelFocusChange() } },
            ) {
                Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(TILE_W.dp * 0.5f, TILE_H.dp * 0.5f).clip(RoundedCornerShape(8.dp))) { AppArt(app) }
                    Spacer(Modifier.width(16.dp))
                    Txt(app.label, Type.heading)
                }
                PanelRow(Icons.Rounded.PlayArrow, "Open", modifier = Modifier.focusRequester(first)) { a.menu = null; a.open(app.pkg) }
                PanelRow(Icons.Rounded.Info, "App info") {
                    a.menu = null
                    startSafely(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                }
                PanelRow(if (pinned) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline, if (pinned) "Remove from bar" else "Add to bar") {
                    a.menu = null; a.toggleFavorite(app.pkg)
                }
            }
        }
    }
}

@Composable
fun PanelRow(icon: ImageVector, label: String, sub: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick, modifier.fillMaxWidth().padding(vertical = 2.dp), corner = 16.dp, scale = 1.02f, fill = true) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, 22.dp, if (f) OnLight else Dim)
            Spacer(Modifier.width(16.dp))
            Column {
                Txt(label, Type.body, color = if (f) OnLight else Ink)
                if (sub != null) Txt(sub, Type.label, color = if (f) OnLight.copy(alpha = 0.6f) else Faint)
            }
        }
    }
}
