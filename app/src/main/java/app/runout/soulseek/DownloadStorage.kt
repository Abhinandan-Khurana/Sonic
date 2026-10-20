package app.runout.soulseek

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.runout.library.FlacPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Shared SAF storage boundary for Soulseek and Internet Archive downloads.
 * The repository owns account checks, renaming, and the database commit.
 */
internal class DownloadStorage(
    private val context: Context,
    private val destination: () -> Uri?,
) {
    fun stagingDirectory(id: String) = File(context.noBackupFilesDir, "soulseek-staging/$id").apply { mkdirs() }

    fun requireDestination(): DocumentFile = destination()?.let { DocumentFile.fromTreeUri(context, it) }?.takeIf { it.canWrite() }
        ?: throw IllegalStateException("Choose a writable download folder first.")

    fun albumDirectory(title: String, id: String): DocumentFile {
        val root = requireDestination()
        val name = SoulseekPolicy.safeName(title) + " " + id.takeLast(10)
        return root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name) ?: throw IOException("Could not create an album folder.")
    }

    suspend fun copyValidated(stage: File, target: Uri, size: Long) {
        val source = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
            stage.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    source.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            output.flush()
        } ?: throw IOException("Your download folder is unavailable. Choose it again and retry.")
        val valid = context.contentResolver.openInputStream(target)?.use { FlacPolicy.isValid(it) } == true
        val actualSize = context.contentResolver.openAssetFileDescriptor(target, "r")?.use { it.length } ?: -1
        val saved = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(target)?.use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                saved.update(buffer, 0, count)
            }
        } ?: throw IOException("The saved FLAC could not be read back.")
        check(source.digest().contentEquals(saved.digest())) { "The saved FLAC does not match the download byte for byte." }
        check(valid && (actualSize < 0 || actualSize == size)) { "The destination could not save the complete FLAC file. Free storage and retry." }
    }

    fun thumbnailsDirectory(): DocumentFile {
        val root = requireDestination()
        return root.findFile("thumbnails")?.takeIf { it.isDirectory } ?: root.createDirectory("thumbnails")
            ?: throw IOException("Could not create the thumbnails folder.")
    }
    fun writeDocument(folder: DocumentFile, name: String, bytes: ByteArray) {
        val mime = if (name.endsWith(".vinyl")) "application/zip" else "application/octet-stream"
        val target = folder.findFile(name) ?: folder.createFile(mime, name) ?: return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) }
    }
}
