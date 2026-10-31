package local.personalhome

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberInitialFocus(): FocusRequester {
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { r.requestFocus() }.isSuccess) return@LaunchedEffect; delay(50) } }
    return r
}

private fun Modifier.focusIf(c: Boolean, r: FocusRequester) = if (c) focusRequester(r) else this

/** Every secondary screen: the scene seen through frosted glass, a quiet title and the clock. */
@Composable
fun Page(a: MainActivity, title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LocalBackdrop.current?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 36.dp, bottom = 8.dp)) {
                Column {
                    Txt(title, Type.display)
                    if (hint != null) Txt(hint, Type.label, Modifier.padding(top = 4.dp), color = Faint)
                }
                Spacer(Modifier.weight(1f))
                Clock(a.prefs.clock24)
            }
            content()
        }
    }
}

/** Full-artwork tile with its name beneath; the name brightens with focus. Hold OK for the app menu. */
@Composable
fun AppCard(a: MainActivity, app: App, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val me = remember { FocusRequester() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            { a.open(app.pkg) }, Modifier.fillMaxWidth().aspectRatio(TILE_W / TILE_H.toFloat()).focusRequester(me),
            onLongClick = { a.showMenu(app, me) }, onFocus = { focused = it },
        ) {
            AppArt(app)
            if (app.pkg in a.prefs.favorites) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Ico(Icons.Rounded.Star, 14.dp) }
        }
        Txt(app.label, Type.label, Modifier.padding(top = 12.dp), color = if (focused) Ink else Faint)
    }
}

@Composable
fun AppGrid(a: MainActivity, apps: List<App>, first: FocusRequester?) {
    LazyVerticalGrid(
        GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.pkg }) { i, app ->
            AppCard(a, app, if (first != null) Modifier.focusIf(i == 0, first) else Modifier)
        }
    }
}

@Composable
fun Quiet(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 56.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 22.dp, Faint)
        Spacer(Modifier.width(12.dp))
        Txt(text, Type.body, color = Faint)
    }
}

@Composable
fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Txt(title, Type.heading, Modifier.padding(start = 56.dp, top = 18.dp), color = Dim)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        content = content,
    )
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(196.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(onClick, Modifier.fillMaxWidth().aspectRatio(TILE_W / TILE_H.toFloat()), onFocus = { focused = it }) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Ico(icon, 34.dp) }
        }
        Txt(label, Type.label, Modifier.padding(top = 12.dp), color = if (focused) Ink else Faint)
    }
}

// ---------- Screens ----------

@Composable
fun AppsScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    Page(a, "Apps", "Hold OK to add or remove an app from Home") { AppGrid(a, a.apps, first) }
}

@Composable
fun MySpaceScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    val byPkg = a.apps.associateBy { it.pkg }
    val recents = a.prefs.recents.mapNotNull { byPkg[it] }
    val home = a.prefs.favorites.mapNotNull { byPkg[it] }
    Page(a, "My Space") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Shelf("Recently opened") {
                itemsIndexed(recents, key = { _, it -> it.pkg }) { i, app -> AppCard(a, app, Modifier.width(196.dp).focusIf(i == 0, first)) }
            }
            if (recents.isEmpty()) Quiet(Icons.Rounded.History, "Apps you open will gather here.")
            Shelf("Your Home collection") {
                itemsIndexed(home, key = { _, it -> it.pkg }) { i, app ->
                    AppCard(a, app, Modifier.width(196.dp).focusIf(recents.isEmpty() && i == 0, first))
                }
            }
            if (home.isEmpty()) Quiet(Icons.Rounded.Star, "Hold OK on any app in Apps to add it here.")
        }
    }
}

@Composable
fun DiscoverScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    val newest = a.apps.sortedByDescending { it.installed }.take(10)
    val store = a.apps.firstOrNull { it.pkg == "com.android.vending" }
    Page(a, "Discover") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Shelf("New on this TV") {
                itemsIndexed(newest, key = { _, it -> it.pkg }) { i, app -> AppCard(a, app, Modifier.width(196.dp).focusIf(i == 0, first)) }
            }
            Shelf("Find more") {
                if (store != null) item { ActionTile(Icons.Rounded.Shop, "Google Play") { a.open(store.pkg) } }
                item { ActionTile(Icons.Rounded.Search, "Search") { a.go(Screen.Search) } }
            }
        }
    }
}

@Composable
fun SearchScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    var q by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val matches = if (q.isBlank()) emptyList() else a.apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
    Page(a, "Search") {
        Row(Modifier.padding(horizontal = 56.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Glass(Modifier.weight(1f).height(60.dp), corner = 30.dp, shadow = false) {
                Row(
                    Modifier.fillMaxSize()
                        .border(2.dp, Color.White.copy(alpha = if (fieldFocused) 0.9f else 0f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.Rounded.Search, 24.dp, Dim)
                    Spacer(Modifier.width(14.dp))
                    BasicTextField(
                        q, { q = it },
                        Modifier.weight(1f).focusRequester(first).onFocusChanged { fieldFocused = it.isFocused },
                        textStyle = Type.heading,
                        singleLine = true,
                        cursorBrush = SolidColor(Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            Box { if (q.isEmpty()) Txt("Search apps", Type.heading, color = Faint); inner() }
                        },
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            GlassButton(Icons.Rounded.Mic, 60.dp) {
                startSafely(a, Intent(Intent.ACTION_ASSIST), Intent("android.search.action.GLOBAL_SEARCH"))
            }
        }
        when {
            q.isBlank() -> Quiet(Icons.Rounded.Search, "Type to find an app, or use the mic to search with Google.")
            matches.isEmpty() -> Quiet(Icons.Rounded.Search, "No apps match “${q.trim()}”.")
            else -> AppGrid(a, matches, null)
        }
    }
}

@Composable
fun WallpaperScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    val p = a.prefs
    var editing by remember { mutableStateOf(false) }
    BackHandler(editing) { editing = false }
    Page(a, "Wallpapers", "An album or all stock photos change photo every minute") {
        if (editing) {
            // The phone sends the album link to a small page served by the TV (a long link is painful to type with the remote).
            val server = remember { runCatching { ServerSocket(8765) }.getOrElse { ServerSocket(0) } }
            DisposableEffect(server) { onDispose { server.close() } }
            val url = remember { "http://${lanIp()}:${server.localPort}" }
            val qr = remember(url) { qrImage(url) }
            LaunchedEffect(server) { awaitAlbumLink(server)?.let { p.album = it; p.wallpaper = WP_ALBUM; editing = false } }
            Glass(Modifier.padding(horizontal = 56.dp, vertical = 12.dp), corner = 28.dp) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                        Image(qr, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                    }
                    Spacer(Modifier.width(32.dp))
                    Column {
                        Txt("Scan with your phone, or open", Type.body, color = Dim)
                        Txt(url, Type.title, Modifier.padding(vertical = 8.dp))
                        Txt("and paste your album's link (Google Photos: Share > Create link).", Type.body, color = Dim)
                    }
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = WP_ALBUM) {
                val on = p.wallpaper == WP_ALBUM
                SourceTile(
                    Icons.Rounded.PhotoLibrary, "Google Photos",
                    if (p.album.isEmpty()) "Add album link" else "Choose album",
                    on, Modifier.focusIf(on, first),
                ) { if (p.album.isNotEmpty()) p.wallpaper = WP_ALBUM; editing = true } // Back keeps the current album
            }
            item(key = WP_STOCK) {
                val on = p.wallpaper == WP_STOCK
                SourceTile(Icons.Rounded.Shuffle, "Stock photos", "Every minute", on, Modifier.focusIf(on, first)) { p.wallpaper = WP_STOCK }
            }
            itemsIndexed(WALLPAPERS, key = { _, it -> it.first }) { _, (key, id) ->
                val thumb by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.Default) { thumbnail(a.resources, id) } }
                Focusable({ p.wallpaper = key }, Modifier.fillMaxWidth().aspectRatio(16 / 9f).focusIf(key == p.wallpaper, first), corner = 16.dp) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (key == p.wallpaper) Picked(Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, label: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick, modifier.fillMaxWidth().aspectRatio(16 / 9f), corner = 16.dp) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Ico(icon, 28.dp, Dim)
            Txt(label, Type.body, Modifier.padding(top = 8.dp))
            Txt(sub, Type.label, color = Faint)
        }
        if (on) Picked(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun Picked(modifier: Modifier) = Box(
    modifier.padding(10.dp).size(28.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center,
) { Ico(Icons.Rounded.Check, 18.dp, OnLight) }

@Composable
fun AppearanceScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    val p = a.prefs
    Page(a, "Appearance") {
        Glass(Modifier.padding(horizontal = 56.dp, vertical = 20.dp).width(720.dp), corner = 28.dp) {
            Column(Modifier.padding(10.dp)) {
                OptionRow("Clock", if (p.clock24) "24-hour" else "12-hour", Modifier.focusRequester(first)) { p.clock24 = !p.clock24 }
                OptionRow("Weather", if (p.weatherOn) "Shown" else "Hidden") { p.weatherOn = !p.weatherOn }
                OptionRow("Temperature", if (p.fahrenheit) "Fahrenheit" else "Celsius") { p.fahrenheit = !p.fahrenheit }
                OptionRow("Wallpaper shading", listOf("Light", "Balanced", "Strong")[p.shade.coerceIn(0, 2)]) { p.shade = (p.shade + 1) % 3 }
                OptionRow("App name above the dock", if (p.focusName) "Shown" else "Hidden") { p.focusName = !p.focusName }
                OptionRow("Music player", if (a.music.granted) "Connected" else "Needs access") {
                    startSafely(a, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            }
        }
    }
}

@Composable
fun StremioScreen(a: MainActivity) {
    val first = rememberInitialFocus()
    val p = a.prefs
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    Page(a, "Stremio", "Your Continue Watching row on Home comes from this account") {
        Column(Modifier.padding(horizontal = 56.dp, vertical = 20.dp).width(820.dp)) {
            if (p.stremioKey.isEmpty()) {
                // A fresh code every 5 minutes in case an unused one expires; stops as soon as it is approved.
                val link by produceState<Pair<String, ImageBitmap?>?>(null) {
                    while (p.stremioKey.isEmpty()) {
                        val created = runCatching { withContext(Dispatchers.IO) { Stremio.linkCreate() } }.getOrNull()
                        if (created == null) { delay(10_000); continue }
                        val (code, qr) = created
                        value = code to remoteImage(qr, 400)
                        repeat(75) {
                            delay(4000)
                            runCatching { withContext(Dispatchers.IO) { Stremio.linkRead(code) } }.getOrNull()?.let { (key, email) ->
                                p.stremioEmail = email; p.stremioKey = key; a.refreshRows(); return@produceState
                            }
                        }
                    }
                }
                Glass(corner = 28.dp) {
                    Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            link?.second?.let { Image(it, null, Modifier.fillMaxSize().padding(10.dp)) }
                        }
                        Spacer(Modifier.width(36.dp))
                        Column {
                            Txt("Scan the code with your phone, or open", Type.body, color = Dim)
                            Txt(link?.let { "link.stremio.com/${it.first}" } ?: "Getting a code…", Type.display, Modifier.padding(vertical = 10.dp))
                            Txt("then sign in to Stremio there to approve this TV.", Type.body, color = Dim)
                        }
                    }
                }
            } else Glass(corner = 28.dp) {
                Column(Modifier.padding(10.dp)) {
                    OptionRow("Signed in", p.stremioEmail, Modifier.focusRequester(first)) {}
                    OptionRow("Copy add-ons to Nuvio", "Lets Nuvio play titles") {
                        msg = "Copying add-ons to Nuvio…"
                        scope.launch { msg = runCatching { "Sent ${Stremio.copyAddonsToNuvio(a, p.stremioKey)} add-ons to Nuvio." }.getOrElse { it.message } }
                    }
                    OptionRow("Sign out", "") { p.stremioKey = ""; a.refreshRows() }
                }
            }
            msg?.let { Txt(it, Type.body, Modifier.padding(top = 18.dp), color = Dim) }
        }
    }
}

@Composable
fun OptionRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick, modifier.fillMaxWidth().padding(vertical = 2.dp), corner = 18.dp, scale = 1.02f, fill = true) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, Type.body, color = if (f) OnLight else Ink)
            Spacer(Modifier.weight(1f))
            Txt(value, Type.body, color = if (f) OnLight.copy(alpha = 0.6f) else Faint)
        }
    }
}

