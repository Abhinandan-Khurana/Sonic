package app.runout.soulseek

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "soulseek_transfers", indices = [Index("owner")])
data class TransferRow(
    @PrimaryKey val id: String,
    val owner: String,
    val username: String,
    val path: String,
    val size: Long,
    val bytes: Long = 0,
    val state: String = SoulseekTransferState.QUEUED.name,
    val queuePosition: Long? = null,
    val speed: Double = 0.0,
    val error: String? = null,
    val stagePath: String? = null,
    val publishedUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val provider: String = "Soulseek",
) {
    fun model() = SoulseekTransfer(id, username, path, size, bytes, SoulseekTransferState.valueOf(state), queuePosition, speed, error, provider)
}
@Entity(tableName = "soulseek_contacts", primaryKeys = ["owner", "username"])
data class ContactRow(val owner: String, val username: String, val online: Boolean = false)
@Entity(tableName = "soulseek_messages", indices = [Index("owner"), Index(value = ["owner", "username"])])
data class MessageRow(
    @PrimaryKey val id: String,
    val owner: String,
    val username: String,
    val text: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val state: String,
    val read: Boolean = false,
) {
    fun model() = SoulseekMessage(id, username, text, timestamp, outgoing, SoulseekMessageState.valueOf(state), read)
}
@Entity(tableName = "soulseek_albums")
data class DownloadAlbumRow(@PrimaryKey val id: String, val json: String)

@Dao
interface SoulseekDao {
    @Query("SELECT * FROM soulseek_transfers WHERE owner = :owner ORDER BY updatedAt ASC, id ASC") fun transfers(owner: String): Flow<List<TransferRow>>
    @Query("SELECT * FROM soulseek_transfers WHERE owner = :owner AND state IN ('QUEUED','PAUSED') ORDER BY updatedAt ASC, id ASC") suspend fun resumable(owner: String): List<TransferRow>
    @Query("SELECT * FROM soulseek_transfers WHERE id = :id") suspend fun transfer(id: String): TransferRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTransfer(row: TransferRow)
    @Query("UPDATE soulseek_transfers SET state = 'PAUSED', speed = 0, error = :reason WHERE state IN ('QUEUED','DOWNLOADING','VALIDATING','PUBLISHING')") suspend fun pauseInterrupted(reason: String)
    @Query("UPDATE soulseek_transfers SET state = 'PAUSED', speed = 0, error = :reason WHERE owner = :owner AND state IN ('QUEUED','DOWNLOADING','VALIDATING','PUBLISHING')") suspend fun pauseOwner(owner: String, reason: String)
    @Query("SELECT * FROM soulseek_contacts WHERE owner = :owner ORDER BY username COLLATE NOCASE") fun contacts(owner: String): Flow<List<ContactRow>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addContact(row: ContactRow)
    @Query("UPDATE soulseek_contacts SET online = :online WHERE owner = :owner AND username = :username") suspend fun presence(owner: String, username: String, online: Boolean)
    @Query("UPDATE soulseek_contacts SET online = 0") suspend fun offlineContacts()
    @Query("SELECT * FROM soulseek_messages WHERE owner = :owner ORDER BY timestamp, id") fun messages(owner: String): Flow<List<MessageRow>>
    @Query("SELECT * FROM soulseek_messages WHERE id = :id") suspend fun message(id: String): MessageRow?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(row: MessageRow): Long
    @Query("UPDATE soulseek_messages SET state = :state WHERE id = :id AND outgoing = 1") suspend fun messageState(id: String, state: String)
    @Query("UPDATE soulseek_messages SET state = 'FAILED' WHERE outgoing = 1 AND state = 'PENDING'") suspend fun failInterruptedMessages()
    @Query("UPDATE soulseek_messages SET read = 1 WHERE owner = :owner AND username = :username") suspend fun markRead(owner: String, username: String)
    @Query("DELETE FROM soulseek_albums WHERE id = :id") suspend fun removeAlbum(id: String)
    @Query("UPDATE soulseek_messages SET state = 'FAILED' WHERE owner = :owner AND outgoing = 1 AND state = 'PENDING'") suspend fun failPendingMessages(owner: String)
    @Query("SELECT * FROM soulseek_albums") fun albums(): Flow<List<DownloadAlbumRow>>
    @Query("SELECT * FROM soulseek_albums WHERE id = :id") suspend fun album(id: String): DownloadAlbumRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAlbum(row: DownloadAlbumRow)
}

@Database(entities = [TransferRow::class, ContactRow::class, MessageRow::class, DownloadAlbumRow::class], version = 1, exportSchema = true)
abstract class SoulseekDatabase : RoomDatabase() { abstract fun soulseek(): SoulseekDao }
