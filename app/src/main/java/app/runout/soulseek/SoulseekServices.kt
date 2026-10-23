package app.runout.soulseek

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.runout.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

/** Services share the process repository and never create a second Soulseek login. */
internal object SoulseekRuntime {
    @Volatile var repository: SoulseekRepository? = null
}

abstract class SoulseekForegroundService : Service() {
    protected abstract val notificationId: Int
    protected abstract val online: Boolean
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Soulseek", NotificationManager.IMPORTANCE_LOW).apply {
            description = "FLAC downloads and your optional online Soulseek session"
        })
        val type = if (online && Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else if (!online && Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, notificationId, notification(if (online) "Soulseek online" else "Downloading FLAC music"), type)
        val repo = SoulseekRuntime.repository
        if (repo == null) { stopSelf(); return }
        if (online) scope.launch {
            repo.session.collect { session -> manager.notify(notificationId,notification(if(session.connected) "Soulseek online · messages and FLAC sharing" else "Soulseek reconnecting · your account is kept")) }
        }
        if (!online) scope.launch {
            // At most one notification update a second, always ending on the newest numbers:
            // progress arrives far more often, and each post wakes the system UI.
            combine(repo.transfers, repo.uploads) { downloads, uploads -> downloads to uploads }.conflate().collect { (transfers, uploads) ->
                val active = transfers.filter { it.state in listOf(SoulseekTransferState.QUEUED, SoulseekTransferState.DOWNLOADING, SoulseekTransferState.VALIDATING, SoulseekTransferState.PUBLISHING) }
                val serving = uploads.filter { it.state == "uploading" || it.state == "queued" }
                if (active.isNotEmpty() || serving.isNotEmpty()) {
                    val bytes = active.sumOf { it.bytes } + serving.sumOf { it.bytes }
                    val total = (active.sumOf { it.size } + serving.sumOf { it.size }).coerceAtLeast(1)
                    manager.notify(notificationId, notification("${active.size} downloads · ${serving.size} uploads", ((bytes.toDouble() / total) * 100).toInt()))
                    delay(NOTIFY_INTERVAL_MS)
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISCONNECT) {
            SoulseekRuntime.repository?.disconnectFromNotification()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String, progress: Int? = null): Notification {
        val open = PendingIntent.getActivity(this, notificationId, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnect = PendingIntent.getService(this, notificationId, Intent(this, javaClass).setAction(DISCONNECT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Sonic.")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Disconnect", disconnect)
            .apply { progress?.let { setProgress(100, it.coerceIn(0, 100), false) } }
            .build()
    }
    // Android 15's dataSync budget is finite. Never restart a timed-out service automatically.
    override fun onTimeout(startId: Int, fgsType: Int) {
        SoulseekRuntime.repository?.onServiceTimeout()
        stopSelf()
    }
    override fun onTimeout(startId: Int) {
        SoulseekRuntime.repository?.onServiceTimeout()
        stopSelf()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private const val CHANNEL = "soulseek"
        private const val DISCONNECT = "app.runout.soulseek.DISCONNECT"
        private const val NOTIFY_INTERVAL_MS = 1_000L
    }
}
class SoulseekTransferService : SoulseekForegroundService() {
    override val notificationId = 8301
    override val online = false
}
class SoulseekOnlineService : SoulseekForegroundService() {
    override val notificationId = 8302
    override val online = true
}
