package app.runout.pressing

import android.content.Context
import app.runout.library.AlbumJson
import app.runout.model.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Records pressed with the old Pressing Plant. The plant itself is gone; the records people made
 * with it stay on the shelf, playable and removable.
 */
class PressingRepository(private val context: Context) {
    private val file = File(context.filesDir, "pressings.json")
    private val coverDir = File(context.filesDir, "covers")

    private val _records = MutableStateFlow(AlbumJson.readValidatedList(context, file))
    val records: StateFlow<List<Album>> = _records.asStateFlow()

    fun delete(id: String) {
        _records.value = _records.value.filterNot { it.id == id }
        AlbumJson.writeList(file, _records.value)
        File(coverDir, "$id.jpg").delete()
    }
}
