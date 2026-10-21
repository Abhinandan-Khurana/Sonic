package app.runout

import app.runout.library.AlbumJson
import app.runout.sharing.VinylPackage
import app.runout.social.Profile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class VinylChecksumTest {
    private val names = listOf("audio/01 Opening.flac", "audio/02 Lowlight.flac")
    private val audio get() = listOf(
        Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac"),
        Fixtures.bytes("Music/Vela Park - Night Drive (2023)/02 Lowlight.flac"),
    )

    private fun sums(): JSONObject = JSONObject().apply {
        names.zip(audio).forEach { (name, bytes) ->
            put(name, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
    }

    private fun packageBytes(checksums: JSONObject?, kind: VinylPackage.Kind = VinylPackage.Kind.ALBUM): ByteArray {
        val album = Fixtures.nightDrive().let { a ->
            a.copy(tracks = a.tracks.mapIndexed { i, t -> t.copy(fileName = names[i]) })
        }
        val manifest = JSONObject().put("format", 1).put("kind", kind.name)
            .put("from", Profile("sender", "Sender", Profile.ACCENTS[0]).toJson())
        if (kind != VinylPackage.Kind.FRIEND) manifest.put("album", AlbumJson.toJson(album, false))
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
                entry("manifest.json", manifest.toString().toByteArray())
                if (kind == VinylPackage.Kind.ALBUM) names.zip(audio).forEach { (name, bytes) -> entry(name, bytes) }
                checksums?.let { entry("checksums.json", it.toString().toByteArray()) }
            }
        }.toByteArray()
    }

    private fun read(bytes: ByteArray) = VinylPackage.read(bytes.inputStream(), File(Fixtures.context.cacheDir, "checksum-import"))

    private fun refused(sums: JSONObject?) {
        try {
            read(packageBytes(sums))
            fail("Audio without a checksum for every entry must be rejected")
        } catch (e: VinylPackage.Invalid) {
            assertTrue(e.message.orEmpty().contains("checksum", ignoreCase = true))
        }
    }

    @Test fun `audio without checksums json is refused`() = refused(null)
    @Test fun `audio with empty checksums is refused`() = refused(JSONObject())
    @Test fun `audio with only some checksums is refused`() = refused(sums().apply { remove(names.last()) })

    @Test fun `complete checksums preserve every FLAC byte`() {
        val got = read(packageBytes(sums().let { all ->
            JSONObject().apply { names.forEach { put(it, all.getString(it).uppercase()) } }
        }))
        names.zip(audio).forEach { (name, bytes) -> assertArrayEquals(bytes, got.audio.getValue(name).readBytes()) }
    }

    @Test fun `cards and friend cards remain valid without audio checksums`() {
        listOf(VinylPackage.Kind.CARD, VinylPackage.Kind.FRIEND).forEach { kind ->
            assertEquals(kind, read(packageBytes(null, kind)).kind)
        }
    }

    @Test fun `wrong checksum is refused`() {
        try {
            read(packageBytes(sums().put(names.first(), "0".repeat(64))))
            fail("Damaged audio must be refused")
        } catch (e: VinylPackage.Invalid) {
            assertTrue(e.message.orEmpty().contains("arrived damaged"))
        }
    }
}
