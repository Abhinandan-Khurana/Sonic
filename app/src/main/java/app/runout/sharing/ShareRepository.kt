package app.runout.sharing

import android.content.Context
import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.library.ArtworkRepository
import app.runout.library.FlacPolicy
import app.runout.library.LibraryScanner
import app.runout.model.Album
import app.runout.model.Source
import app.runout.social.ShelfItem
import app.runout.social.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

sealed interface ImportResult {
    data class AlbumAdded(val album: Album) : ImportResult
    data class CardAdded(val fromName: String, val album: Album) : ImportResult
    data class FriendAdded(val name: String, val isNew: Boolean, val records: Int) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * Sending and receiving records and friend cards as `.vinyl` files. No server: the file goes
 * through Android's share sheet (Quick Share, WhatsApp, Telegram, Drive...).
 */
class ShareRepository(
    private val context: Context,
    private val social: SocialRepository,
) {
    private val receivedDir = File(context.filesDir, "received")
    private val listFile = File(context.filesDir, "received.json")
    private val shareDir = File(context.cacheDir, "share")

    init {
        // Only abandoned private imports, never published rx-/card- folders or user music.
        receivedDir.listFiles()?.filter { it.name.startsWith("incoming-") }
            ?.forEach { discardStaging(it) }
    }

    private val _received = MutableStateFlow(AlbumJson.readValidatedList(context, listFile))
    val received: StateFlow<List<Album>> = _received.asStateFlow()

    /**
     * Packs a record. Its audio goes along only when the record may be shared — your own music,
     * a Creative Commons licence, or a record that arrived that way. Otherwise it's a card.
     */
    suspend fun exportAlbum(album: Album): File = withContext(Dispatchers.IO) {
        require(album.tracks.isNotEmpty()) { "A record needs at least one FLAC track" }
        require(album.tracks.all { FlacPolicy.read(context, it) != null }) { "Only verified FLAC music can be exported" }
        val withAudio = album.audioShareable
        shareDir.mkdirs()
        val file = File(shareDir, safeName("${album.artist} - ${album.title}") + ".${VinylPackage.EXTENSION}")
        val entries = album.tracks.mapIndexed { i, t ->
            val ext = t.fileName.substringAfterLast('.', "").ifBlank { album.quality.codec.lowercase() }
            VinylPackage.entryNameFor(i, t.title, ext)
        }
        val packed = album.copy(
            tracks = album.tracks.mapIndexed { i, t -> t.copy(fileName = if (withAudio) entries[i] else "") },
        )
        FileOutputStream(file).use { out ->
            VinylPackage.write(
                out = out,
                kind = if (withAudio) VinylPackage.Kind.ALBUM else VinylPackage.Kind.CARD,
                from = social.profile.value,
                album = packed,
                sleeve = album.coverPath?.let(::File),
                audio = if (!withAudio) emptyList() else album.tracks.mapIndexed { i, t ->
                    entries[i] to { openTrack(t.uri) }
                },
            )
        }
        file
    }

    suspend fun exportFriendCard(shelf: List<Album>): File = withContext(Dispatchers.IO) {
        shareDir.mkdirs()
        val me = social.profile.value
        val file = File(shareDir, safeName("${me.name} on Sonic") + ".${VinylPackage.EXTENSION}")
        FileOutputStream(file).use { out ->
            VinylPackage.write(out, VinylPackage.Kind.FRIEND, me, album = null, shelf = shelf.take(200).map { ShelfItem.from(it) })
        }
        file
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return@withContext ImportResult.Failed("Couldn't open the file")
        input.use { importStream(it) }
    }

    suspend fun importStream(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val staging = File(receivedDir, "incoming-${System.nanoTime()}")
        val contents = try {
            VinylPackage.read(input, staging)
        } catch (e: Throwable) {
            try { discardStaging(staging) } catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            // Cancellation and fatal VM errors are not user-facing import failures.
            if (e is CancellationException || e !is Exception) throw e
            return@withContext ImportResult.Failed(e.message ?: "Not a Sonic record")
        }
        when (contents.kind) {
            VinylPackage.Kind.FRIEND -> {
                discardStaging(staging)
                if (contents.from.id == social.profile.value.id) return@withContext ImportResult.Failed("That's your own friend card")
                val isNew = social.upsertFriend(contents.from, contents.shelf)
                ImportResult.FriendAdded(contents.from.name, isNew, contents.shelf.size)
            }
            VinylPackage.Kind.CARD -> {
                val album = contents.album!!
                val id = "card-" + LibraryScanner.stableId(contents.from.id + "|" + album.id)
                val dir = File(receivedDir, id).also { it.deleteRecursively() }
                staging.renameTo(dir)
                val card = album.copy(
                    id = id,
                    source = Source.RECEIVED,
                    from = contents.from.name,
                    coverPath = contents.sleeve?.let { File(dir, it.name).takeIf { f -> f.exists() }?.absolutePath },
                    tracks = album.tracks.map { it.copy(uri = "") },
                )
                social.addCard(contents.from, card)
                ImportResult.CardAdded(contents.from.name, card)
            }
            VinylPackage.Kind.ALBUM -> {
                val album = contents.album!!
                val id = "rx-" + LibraryScanner.stableId(contents.from.id + "|" + album.id)
                val dir = File(receivedDir, id).also { it.deleteRecursively() }
                if (!staging.renameTo(dir)) {
                    staging.copyRecursively(dir, overwrite = true)
                    discardStaging(staging)
                }
                val received = album.copy(
                    id = id,
                    source = Source.RECEIVED,
                    from = contents.from.name,
                    folderUri = dir.toURI().toString(),
                    coverPath = File(dir, "sleeve.jpg").takeIf { it.exists() }?.absolutePath,
                    tint = File(dir, "sleeve.jpg").takeIf { it.exists() }?.let { ArtworkRepository.averageColor(it) } ?: album.tint,
                    tracks = album.tracks.map { t ->
                        val f = File(dir, t.fileName)
                        t.copy(id = "$id/${t.fileName}", uri = Uri.fromFile(f).toString())
                    },
                )
                _received.value = (_received.value.filterNot { it.id == id } + received)
                AlbumJson.writeList(listFile, _received.value)
                // Receiving a record from someone also makes them a friend.
                social.addCard(contents.from, received.copy(tracks = received.tracks.map { it.copy(uri = "") }))
                ImportResult.AlbumAdded(received)
            }
        }
    }

    fun delete(albumId: String) {
        _received.value = _received.value.filterNot { it.id == albumId }
        AlbumJson.writeList(listFile, _received.value)
        File(receivedDir, albumId).deleteRecursively()
    }

    /** Walk without FOLLOW_LINKS so cleanup can never traverse into published music. */
    private fun discardStaging(staging: File) {
        if (!Files.exists(staging.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(staging.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun openTrack(uri: String): InputStream? = runCatching {
        val u = Uri.parse(uri)
        if (u.scheme == "file") File(u.path!!).inputStream() else context.contentResolver.openInputStream(u)
    }.getOrNull()

    private fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(80).ifBlank { "Record" }
}
