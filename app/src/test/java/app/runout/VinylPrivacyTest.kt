package app.runout

import android.app.Application
import app.runout.model.Source
import app.runout.sharing.ShareRepository
import app.runout.sharing.VinylPackage
import app.runout.social.Profile
import app.runout.social.SocialRepository
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekSession
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VinylPrivacyTest {
    private fun manifest(bytes: ByteArray): JSONObject = ZipInputStream(bytes.inputStream()).use { zip ->
        check(zip.nextEntry.name == "manifest.json")
        JSONObject(zip.readBytes().decodeToString())
    }

    @Test fun `album and card manifests omit private provenance and notes without changing local metadata`() {
        val album = Fixtures.nightDrive().copy(source = Source.SOULSEEK, from = "fixture-peer", notes = "private liner draft")
        val profile = Profile("sender", "Public display name", Profile.ACCENTS[0])
        listOf(VinylPackage.Kind.ALBUM, VinylPackage.Kind.CARD).forEach { kind ->
            val entries = album.tracks.mapIndexed { i, t -> VinylPackage.entryNameFor(i, t.title, "flac") }
            val packed = album.copy(tracks = album.tracks.mapIndexed { i, t -> t.copy(fileName = entries[i]) })
            val out = ByteArrayOutputStream()
            VinylPackage.write(out, kind, profile, packed, audio = if (kind == VinylPackage.Kind.CARD) emptyList()
                else packed.tracks.mapIndexed { i, _ -> entries[i] to { java.io.File(android.net.Uri.parse(album.tracks[i].uri).path!!).inputStream() } })
            val exported = manifest(out.toByteArray()).getJSONObject("album")
            listOf("source", "from", "notes").forEach { key -> assertFalse("$kind exposes $key", exported.has(key)) }
            assertEquals(album.title, exported.getString("title"))
            assertEquals(album.license, exported.getString("license"))
        }
        val local = app.runout.library.AlbumJson.toJson(album)
        assertEquals("SOULSEEK", local.getString("source"))
        assertEquals("fixture-peer", local.getString("from"))
        assertEquals("private liner draft", local.getString("notes"))
    }

    @Test fun `all exported package kinds omit legacy profile usernames`() = runBlocking {
        val social = SocialRepository(Fixtures.context).apply {
            adopt(Profile("sender", "Public display name", Profile.ACCENTS[0], "fixture-login"))
        }
        val repo = ShareRepository(Fixtures.context, social)
        val manifests = listOf(
            manifest(repo.exportAlbum(Fixtures.nightDrive()).readBytes()),
            manifest(repo.exportAlbum(Fixtures.nightDrive(license = null)).readBytes()),
            manifest(repo.exportFriendCard(emptyList()).readBytes()),
        )
        assertEquals(setOf("ALBUM", "CARD", "FRIEND"), manifests.map { it.getString("kind") }.toSet())
        manifests.forEach { json ->
            assertFalse("${json.getString("kind")} exposes profile.u", json.getJSONObject("from").has("u"))
            assertEquals("Public display name", json.getJSONObject("from").getString("name"))
        }
        assertEquals("fixture-login", social.profile.value.username)
    }

    @Test fun `authenticated Soulseek session does not replace the portable display identity`() {
        val app = Fixtures.context.applicationContext as Application
        SocialRepository(app).rename("Public display name")
        val graph = AppGraph(app)
        try {
            ShadowLooper.idleMainLooper()
            val connection = ReflectionHelpers.getField<Any>(graph.soulseek, "connection")
            val session = ReflectionHelpers.getField<MutableStateFlow<SoulseekSession>>(connection, "_session")
            session.value = SoulseekSession(SoulseekConnection.CONNECTED, "fixture-login", authenticated = true)
            ShadowLooper.idleMainLooper()
            assertEquals("Public display name", graph.social.profile.value.name)
            assertFalse(graph.social.profile.value.toJson().has("u"))
        } finally {
            graph.scope.cancel()
        }
    }
}
