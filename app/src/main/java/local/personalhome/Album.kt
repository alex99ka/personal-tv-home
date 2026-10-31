package local.personalhome

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

/** The Google Photos share link inside whatever the phone pasted (the share sheet may add text around it). */
fun albumLink(input: String): String? =
    Regex("""https://(photos\.app\.goo\.gl|photos\.google\.com)/[^\s"'<>]+""").find(input)?.value

/** This TV's address on the home network, for the phone to reach [awaitAlbumLink]. */
fun lanIp(): String? = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress

fun qrImage(text: String): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) 0xFF000000.toInt() else -1 }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Serves a one-field page to the phone until it sends a shared album link that has photos; returns it,
 * or null once [server] is closed. Only Google Photos links are accepted from the network.
 */
suspend fun awaitAlbumLink(server: ServerSocket): String? = withContext(Dispatchers.IO) {
    while (true) {
        val c = runCatching { server.accept() }.getOrNull() ?: return@withContext null
        val link = runCatching {
            c.use {
                it.soTimeout = 5000
                val r = it.getInputStream().bufferedReader()
                val path = r.readLine().orEmpty().split(' ').getOrElse(1) { "/" }
                while (!r.readLine().isNullOrEmpty()) Unit // headers
                val sent = path.substringAfter("?link=", "").substringBefore('&').let { s -> URLDecoder.decode(s, "UTF-8") }
                val link = albumLink(sent)?.takeIf { l -> albumPhotos(l).isNotEmpty() }
                val note = when {
                    sent.isBlank() -> ""
                    link != null -> "<p class=ok>Saved. The TV is showing your album.</p>"
                    else -> "<p class=bad>That didn't work. Paste a shared album link (Share &gt; Create link).</p>"
                }
                it.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n" + page(note)).toByteArray())
                link
            }
        }.getOrNull()
        if (link != null) return@withContext link
    }
    null
}

private fun page(note: String) = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>TV wallpaper</title><style>body{font:17px system-ui;background:#111318;color:#eee;margin:0;padding:28px 18px}
input,button{font:inherit;width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid #444;margin-top:12px}
input{background:#1d2027;color:#eee}button{background:#eee;color:#111;border:0;font-weight:600}.ok{color:#8fd694}.bad{color:#f39a8f}p{color:#aaa}</style>
<h2>TV wallpaper album</h2>$note<p>In Google Photos open the album, tap Share &gt; Create link, copy it and paste it here.</p>
<form><input name=link type=url placeholder="https://photos.app.goo.gl/…" required><button>Show on TV</button></form>"""
