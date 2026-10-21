package app.runout

import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.sharing.ImportResult
import app.runout.sharing.ShareRepository
import app.runout.sharing.VinylPackage
import app.runout.social.Profile
import app.runout.social.SocialRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class VinylOwnershipTest {
    private fun senderPackage(kind: VinylPackage.Kind, license: String?): ByteArray {
        val album = Fixtures.nightDrive(owned = true, license = license).let { a ->
            a.copy(tracks = a.tracks.mapIndexed { i, t -> t.copy(fileName = VinylPackage.entryNameFor(i, t.title, "flac")) })
        }
        return ByteArrayOutputStream().also { out ->
            VinylPackage.write(out, kind, Profile("sender", "Sender", Profile.ACCENTS[0]), album,
                audio = if (kind == VinylPackage.Kind.CARD) emptyList() else album.tracks.map { t ->
                    t.fileName to { File(Uri.parse(t.uri).path!!).inputStream() }
                })
        }.toByteArray()
    }

    @Test fun `sender ownership is not receiver ownership at the package import boundary`() {
        val bytes = senderPackage(VinylPackage.Kind.ALBUM, null)
        ZipInputStream(bytes.inputStream()).use { zip ->
            zip.nextEntry
            assertTrue(JSONObject(zip.readBytes().decodeToString()).getJSONObject("album").getBoolean("owned"))
        }
        val contents = VinylPackage.read(bytes.inputStream(), File(Fixtures.context.cacheDir, "ownership-read"))
        assertFalse("Import must never transfer ownership", contents.album!!.owned)
        assertFalse(contents.album.audioShareable)
    }

    @Test fun `owned only audio import is retained but reshared only as a card`() = runBlocking {
        val social = SocialRepository(Fixtures.context)
        val repo = ShareRepository(Fixtures.context, social)
        val received = (repo.importStream(senderPackage(VinylPackage.Kind.ALBUM, null).inputStream()) as ImportResult.AlbumAdded).album
        assertFalse(received.owned)
        assertFalse(received.audioShareable)
        assertTrue(received.tracks.all { File(Uri.parse(it.uri).path!!).exists() })
        val persisted = AlbumJson.readList(File(Fixtures.context.filesDir, "received.json")).single()
        assertFalse(persisted.owned)
        assertFalse(ShareRepository(Fixtures.context, social).received.value.single().owned)
        val card = repo.exportAlbum(received)
        java.util.zip.ZipFile(card).use { zip ->
            assertTrue(zip.entries().toList().none { it.name.startsWith("audio/") })
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString())
            assertEquals("CARD", manifest.getString("kind"))
        }
    }

    @Test fun `card imports cannot confer ownership either`() = runBlocking {
        val social = SocialRepository(Fixtures.context)
        val repo = ShareRepository(Fixtures.context, social)
        val received = (repo.importStream(senderPackage(VinylPackage.Kind.CARD, null).inputStream()) as ImportResult.CardAdded).album
        assertFalse(received.owned)
        assertFalse(social.friends.value.single().cards.single().owned)
        assertFalse(SocialRepository(Fixtures.context).friends.value.single().cards.single().owned)
    }

    @Test fun `open licence survives import without ownership`() = runBlocking {
        val repo = ShareRepository(Fixtures.context, SocialRepository(Fixtures.context))
        val received = (repo.importStream(senderPackage(VinylPackage.Kind.ALBUM, "https://creativecommons.org/licenses/by/4.0/").inputStream()) as ImportResult.AlbumAdded).album
        assertFalse(received.owned)
        assertTrue(received.isOpenLicence)
        assertTrue(received.audioShareable)
        java.util.zip.ZipFile(repo.exportAlbum(received)).use { zip ->
            assertEquals(2, zip.entries().toList().count { it.name.startsWith("audio/") })
        }
    }
}
