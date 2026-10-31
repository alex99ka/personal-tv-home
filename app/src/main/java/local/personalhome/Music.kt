package local.personalhome

import android.media.session.PlaybackState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// The music player: a composition above the dock on Home, and the full-screen Listen view it expands into.
// Both share the artwork and the Glass Current ribbon, so the expansion reads as one continuous object.

private val ArtShape = RoundedCornerShape(20.dp)
private const val SEEK_STEP = 15_000L

private fun NowPlaying.can(action: Long) = ((state?.actions ?: 0L) and action) != 0L

@OptIn(ExperimentalSharedTransitionApi::class)
class Shared(val scope: SharedTransitionScope, val visible: AnimatedVisibilityScope) {
    @Composable
    fun Modifier.shared(key: String): Modifier = with(scope) {
        sharedBounds(
            rememberSharedContentState(key), visible,
            boundsTransform = { _, _ -> tween(420, easing = Settle) },
            clipInOverlayDuringTransition = OverlayClip(ArtShape),
        )
    }
}

/** Home's player, right above the dock: artwork, title, artist, controls, then the ribbon. */
@Composable
fun MusicDeck(a: MainActivity, n: NowPlaying, s: Shared) {
    val listen = remember { FocusRequester() }
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) { // back from the Listen view: focus returns to the Listen pill (the dock clears the flag)
        if (!a.listenReturn) return@LaunchedEffect
        repeat(20) { if (runCatching { listen.requestFocus() }.isSuccess) return@LaunchedEffect; delay(30) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 90.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            with(s) { Art(n, 150.dp, Modifier.shared("art")) }
            Spacer(Modifier.width(28.dp))
            Column {
                Txt("NOW PLAYING", Type.caption, color = Faint)
                Txt(n.title, Type.title, Modifier.padding(top = 4.dp))
                Txt(n.artist, Type.body, color = Dim)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Controls(n, 48.dp, Modifier.focusRequester(play))
                    Spacer(Modifier.width(6.dp))
                    Pill(Icons.Rounded.OpenInFull, "Listen", Modifier.focusRequester(listen)) { a.listen = true }
                    Pill(Icons.Rounded.Launch, "Open app") { a.open(n.controller.packageName) }
                }
            }
        }
        // Baseline (the ribbon's middle, 22dp into it) sits about 25dp above the dock.
        with(s) { GlassCurrent(n, play, Modifier.padding(top = 10.dp, bottom = 3.dp).shared("ribbon")) }
    }
}

/** The artwork filling the screen's centre stage; Back shrinks it home and puts focus back on Listen. */
@Composable
fun ListenView(a: MainActivity, n: NowPlaying, s: Shared) {
    val first = rememberInitialFocus()
    BackHandler { a.listenReturn = true; a.listen = false }
    Box(Modifier.fillMaxSize().padding(horizontal = 90.dp, vertical = 48.dp)) {
        Row(Modifier.align(Alignment.Center).padding(bottom = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            with(s) { Art(n, 320.dp, Modifier.shared("art")) }
            Spacer(Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                Txt("NOW PLAYING", Type.caption, color = Faint)
                Txt(n.title, Type.display, Modifier.padding(top = 6.dp))
                Txt(n.artist, Type.heading, Modifier.padding(top = 2.dp), color = Dim)
                Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Controls(n, 60.dp, Modifier.focusRequester(first))
                    Spacer(Modifier.width(6.dp))
                    Pill(Icons.Rounded.Launch, "Open app") { a.open(n.controller.packageName) }
                }
            }
        }
        with(s) { GlassCurrent(n, first, Modifier.align(Alignment.BottomCenter).shared("ribbon")) }
    }
}

@Composable
private fun Art(n: NowPlaying, size: Dp, modifier: Modifier) {
    Box(modifier.size(size).clip(ArtShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        if (n.art != null) Image(n.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        else Ico(Icons.Rounded.MusicNote, size * 0.32f, Dim)
    }
}

/** Only the buttons the app's media session says it supports. [playFocus] goes on play/pause. */
@Composable
private fun Controls(n: NowPlaying, size: Dp, playFocus: Modifier = Modifier) {
    val c = n.controller.transportControls
    if (n.can(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) GlassButton(Icons.Rounded.SkipPrevious, size * 0.84f) { c.skipToPrevious() }
    if (n.can(PlaybackState.ACTION_PLAY_PAUSE) || n.can(if (n.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY))
        GlassButton(if (n.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, size, playFocus) { if (n.playing) c.pause() else c.play() }
    if (n.can(PlaybackState.ACTION_SKIP_TO_NEXT)) GlassButton(Icons.Rounded.SkipNext, size * 0.84f) { c.skipToNext() }
}

/** Quiet rounded secondary action. */
@Composable
fun Pill(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Glass(modifier, corner = 22.dp, shadow = false) {
        Focusable(onClick, Modifier.height(44.dp), corner = 22.dp, scale = 1.05f, fill = true) { f ->
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(icon, 18.dp, if (f) OnLight else Dim)
                Spacer(Modifier.width(8.dp))
                Txt(label, Type.label, color = if (f) OnLight else Ink)
            }
        }
    }
}

/**
 * Glass Current: a gently curved line of light, lit for the elapsed part. Its slow drift is decoration, not a
 * visualiser. Focus it and Left/Right seek 15 s, when the app allows seeking.
 */
@Composable
fun GlassCurrent(n: NowPlaying, up: FocusRequester, modifier: Modifier = Modifier) {
    rememberNow(250) // redraws the position
    var pending by remember(n.state) { mutableStateOf<Long?>(null) } // a seek the app hasn't reported back yet
    val pos = pending ?: n.position()
    val canSeek = n.duration > 0 && n.can(PlaybackState.ACTION_SEEK_TO)
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(210, easing = Settle), label = "ribbonFocus")
    val p by animateFloatAsState(if (n.duration > 0) pos.toFloat() / n.duration else 0f, tween(260, easing = Settle), label = "ribbon")
    val drift = remember { Animatable(0f) } // flows only while playing; paused, the curve holds still
    LaunchedEffect(n.playing) { if (n.playing) while (true) drift.animateTo(drift.value + (2 * PI).toFloat(), tween(11_000, easing = LinearEasing)) }
    Column(modifier.fillMaxWidth()) {
        if (n.duration > 0) Row(Modifier.fillMaxWidth()) {
            Txt(fmtTime(pos), Type.tnum, color = if (focused) Ink else Dim)
            Spacer(Modifier.weight(1f))
            Txt("-" + fmtTime(n.duration - pos), Type.tnum, color = if (focused) Ink else Dim)
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).then(
                if (!canSeek) Modifier else Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .focusProperties { this.up = up } // the ribbon is wide; plain search may pick a far control
                    .onKeyEvent { e ->
                        val step = when (e.key) { Key.DirectionLeft -> -SEEK_STEP; Key.DirectionRight -> SEEK_STEP; else -> return@onKeyEvent false }
                        if (e.type == KeyEventType.KeyDown) {
                            val to = (pos + step).coerceIn(0, n.duration)
                            pending = to
                            n.controller.transportControls.seekTo(to)
                        }
                        true
                    }
                    .focusable()
            ),
        ) { drawCurrent(p, drift.value, amp = (11f + 3f * f) * density, stroke = 4.5f * density, focus = f) }
    }
}

/**
 * The ribbon itself, shared by the player and the cards: ends pinned to the baseline with a soft travelling
 * swell between them, a glowing lit part for elapsed time and a subdued remainder.
 */
fun DrawScope.drawCurrent(p: Float, drift: Float, amp: Float, stroke: Float, focus: Float = 0f) {
    val w = size.width
    val mid = size.height / 2
    fun y(x: Float) = mid + amp * sin(x / w * PI.toFloat()) * sin(x / w * 2.6f * PI.toFloat() + drift)
    fun path(from: Float, to: Float) = Path().apply {
        moveTo(from, y(from))
        var x = from
        while (x < to) { x = min(x + 6f, to); lineTo(x, y(x)) }
    }
    val split = w * p.coerceIn(0f, 1f)
    drawPath(path(split, w), Ink.copy(alpha = 0.26f), style = Stroke(stroke, cap = StrokeCap.Round))
    val lit = path(0f, split)
    drawPath(lit, Ink.copy(alpha = 0.14f + 0.14f * focus), style = Stroke(stroke * 3.5f, cap = StrokeCap.Round)) // illumination
    drawPath(lit, Ink, style = Stroke(stroke, cap = StrokeCap.Round))
    val head = Offset(split, y(split))
    if (focus > 0f) drawCircle(Ink.copy(alpha = 0.22f * focus), radius = stroke * 3.5f, center = head)
    drawCircle(Ink, radius = stroke * (0.9f + 0.6f * focus), center = head)
}
