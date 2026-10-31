package local.personalhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test fun albumLinkFoundInSharedText() {
        assertEquals("https://photos.app.goo.gl/AbC123", albumLink("Look: https://photos.app.goo.gl/AbC123 "))
        assertEquals(null, albumLink("https://evil.test/photos.app.goo.gl/x"))
    }

    @Test fun blurKeepsFlatColour() {
        val px = IntArray(8 * 4) { 0xFF336699.toInt() }
        boxBlur(px, 8, 4, 3)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test fun blurSpreadsABrightPixel() {
        val px = IntArray(9 * 9) { 0xFF000000.toInt() }
        px[4 * 9 + 4] = 0xFFFFFFFF.toInt()
        boxBlur(px, 9, 9, 1)
        val centre = px[4 * 9 + 4] and 255
        assertEquals(255 / 9, centre)                  // 1/3 horizontally, then 1/3 vertically
        assertEquals(centre, px[3 * 9 + 3] and 255)    // diagonal neighbour gets the same share
        assertEquals(0, px[0] and 255)
    }

    @Test fun playerLinkCarriesDeflatedStream() {
        val json = """{"url":"https://x.test/a b.mkv","name":"TB"}"""
        val link = playerLink(json, "https://addon.test/k=1/manifest.json", "https://meta.test/manifest.json", "series", "tt1", "tt1:1:5")
        val parts = link.removePrefix("stremio:///player/").split('/')
        assertEquals(6, parts.size)
        val dec = { s: String -> java.net.URLDecoder.decode(s, "UTF-8") }
        val raw = java.util.Base64.getDecoder().decode(dec(parts[0]))
        assertEquals(json, java.util.zip.InflaterInputStream(raw.inputStream()).readBytes().decodeToString())
        assertEquals("https://addon.test/k=1/manifest.json", dec(parts[1]))
        assertEquals(listOf("https://meta.test/manifest.json", "series", "tt1", "tt1:1:5"), parts.drop(2).map(dec))
    }

    @Test fun weatherCodes() {
        assertEquals(Sky.Clear, skyOf(113))
        assertEquals(Sky.Cloud, skyOf(122))
        assertEquals(Sky.Snow, skyOf(338))
        assertEquals(Sky.Storm, skyOf(389))
        assertEquals(Sky.Rain, skyOf(296))
        assertEquals(Sky.Fog, skyOf(248))
    }
}
