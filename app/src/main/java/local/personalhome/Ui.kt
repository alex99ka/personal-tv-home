package local.personalhome

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- Type ----------

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Inter = FontFamily(inter(300), inter(400), inter(500), inter(600))

val Ink = Color(0xFFF5F5F7)
val Dim = Ink.copy(alpha = 0.70f)
val Faint = Ink.copy(alpha = 0.46f)
val OnLight = Color(0xFF16181D)

private fun style(size: Int, weight: Int, track: Float = 0f) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = FontWeight(weight), color = Ink, letterSpacing = track.sp)

object Type {
    val display = style(40, 300, -0.6f)
    val clock = style(32, 300, -0.4f).copy(fontFeatureSettings = "tnum")
    val title = style(26, 500, -0.3f)
    val heading = style(20, 500, -0.1f)
    val body = style(17, 400)
    val label = style(15, 400)
    val caption = style(13, 500, 1.4f)
    val tnum = style(13, 400).copy(fontFeatureSettings = "tnum")
}

@Composable
fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = style.color) =
    BasicText(text, modifier, style.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun Ico(v: ImageVector, size: Dp, color: Color = Ink, modifier: Modifier = Modifier) =
    Image(v, null, modifier.size(size), colorFilter = ColorFilter.tint(color))

// ---------- Motion ----------

/** Starts confidently, then decelerates gently into place. Used for panels and focus. */
val Settle = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A very restrained spring for small things (card flips); retargets smoothly when interrupted. */
fun <T> soft() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

// ---------- Glass ----------

/** Blurred copy of whatever is behind the UI, drawn full-screen-aligned inside glass shapes. */
val LocalBackdrop = compositionLocalOf<ImageBitmap?> { null }
val LocalRootSize = compositionLocalOf { IntSize.Zero }

/** Soft grounding shadow. shortcut: BlurMaskFilter is ignored by HW canvas before API 28 (hard edge there). */
fun Modifier.softShadow(radius: Dp, dy: Dp, alpha: Float, corner: Dp) = drawBehind {
    if (alpha <= 0f) return@drawBehind
    drawIntoCanvas { c ->
        val p = android.graphics.Paint().apply {
            color = android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0)
            maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val inset = radius.toPx() / 2
        c.nativeCanvas.drawRoundRect(inset, dy.toPx(), size.width - inset, size.height + dy.toPx(), corner.toPx(), corner.toPx(), p)
    }
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    tint: Float = 0.10f,
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val back = LocalBackdrop.current
    val root = LocalRootSize.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .then(if (shadow) Modifier.softShadow(36.dp, 14.dp, 0.32f, corner) else Modifier)
            .onGloballyPositioned { pos = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (back != null && root != IntSize.Zero) drawImage(
                    back, dstOffset = IntOffset(-pos.x.roundToInt(), -pos.y.roundToInt()), dstSize = root,
                    filterQuality = FilterQuality.Low,
                )
            }
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = tint + 0.05f), Color.White.copy(alpha = tint * 0.6f))))
            .background(Color.Black.copy(alpha = 0.06f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))), shape),
        content = content,
    )
}

// ---------- Focus ----------

/**
 * The one focus treatment everything shares: a spring scale, a fine bright edge and a lifted shadow.
 * [fill] = rows and buttons that invert to a light surface when focused instead of growing much.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    scale: Float = 1.05f,
    fill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(210, easing = Settle), label = "focus")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; onFocus(it.isFocused) } }
            // A held remote OK repeats its key-down; the first repeat is the long press, and its key-up must not click.
            .onPreviewKeyEvent { e ->
                if (onLongClick == null || !e.isOk) false
                else if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) { if (!held) { held = true; onLongClick() }; true }
                else if (e.type == KeyEventType.KeyUp && held) { held = false; true }
                else false
            }
            .graphicsLayer { val s = 1f + (scale - 1f) * f; scaleX = s; scaleY = s; translationY = -3.dp.toPx() * f } // slight lift
            .softShadow(24.dp, 10.dp, 0.42f * f, corner)
            .clip(shape)
            .then(if (fill) Modifier.background(Ink.copy(alpha = 0.94f * f)) else Modifier)
            .combinedClickable(interactionSource = null, indication = null, onLongClick = onLongClick, onClick = onClick)
            .drawWithContent {
                drawContent()
                if (!fill && f > 0f) drawRoundRect(
                    Color.White.copy(alpha = 0.9f * f), cornerRadius = CornerRadius(corner.toPx()), style = Stroke(2.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) { content(focused) }
}

/** An app's own TV artwork, edge to edge. Phone-only apps get their icon on a backdrop sampled from it. */
@Composable
fun AppArt(app: App, modifier: Modifier = Modifier) {
    if (app.isBanner) {
        Image(app.art, null, modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
    } else {
        Box(modifier.fillMaxSize().background(app.bg), contentAlignment = Alignment.Center) {
            Image(app.art, null, Modifier.size(52.dp), filterQuality = FilterQuality.High)
        }
    }
}

val KeyEvent.isOk get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/** Swallows the rest of a held OK that opened this, so the hold doesn't also press what now has focus. */
@Composable
fun Modifier.ignoreHeldOk(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { e ->
        if (!armed && e.isOk) { if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { armed = true; false } else true } else false
    }
}

// ---------- Time ----------

@Composable
fun rememberNow(periodMs: Long): Long = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        delay(periodMs - System.currentTimeMillis() % periodMs)
        value = System.currentTimeMillis()
    }
}.value

@Composable
fun Clock(h24: Boolean, modifier: Modifier = Modifier) {
    val now = rememberNow(1000)
    Txt(SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)), Type.clock, modifier)
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
