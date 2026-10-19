package app.runout.soulseek

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The encrypted payload lives in noBackupFilesDir; neither it nor its Keystore key is backed up. */
internal class SoulseekCredentialVault(context: Context) {
    private val file = File(context.noBackupFilesDir, "soulseek-account.enc")
    private val alias = context.packageName + ".soulseek.credentials"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(username: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = JSONObject().put("username", username).put("password", password).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(bytes)
        bytes.fill(0)
        val payload = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(payload.toString())
        check(temporary.renameTo(file)) { "Could not save this account securely" }
    }
    @Synchronized fun load(): Pair<String, String>? = runCatching {
        if (!file.exists()) return null
        val payload = JSONObject(file.readText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(payload.getString("iv"), Base64.NO_WRAP)))
        }
        val plain = cipher.doFinal(Base64.decode(payload.getString("data"), Base64.NO_WRAP))
        try { JSONObject(String(plain, Charsets.UTF_8)).let { it.getString("username") to it.getString("password") } } finally { plain.fill(0) }
    }.getOrNull()
    @Synchronized fun clear() { file.delete(); File(file.parentFile, file.name + ".tmp").delete() }
}
