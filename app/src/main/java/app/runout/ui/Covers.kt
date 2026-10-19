package app.runout.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.runout.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decoded covers, shared by sleeves, record labels and the dragged disc. */
object CoverCache {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun peek(path: String?): ImageBitmap? = path?.let { cache.get(it) }

    suspend fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (!file.exists()) return@runCatching null
                val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        } ?: return null
        cache.put(path, bmp)
        return bmp
    }
}

@Composable
fun rememberCover(album: Album?): State<ImageBitmap?> {
    val path = album?.coverPath
    return produceState(initialValue = CoverCache.peek(path), path) {
        value = path?.let { CoverCache.load(it) }
    }
}
