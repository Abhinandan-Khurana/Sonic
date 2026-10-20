package app.runout.soulseek

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Consent is scoped to an exact account. Folder grants remain Android's responsibility. */
class SoulseekPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("soulseek-setup", Context.MODE_PRIVATE)
    // A backed-up library cache is not evidence that this device completed SAF setup.
    private val existingInstall = java.io.File(context.noBackupFilesDir,"soulseek-account.enc").exists()
    private val _onboarded = MutableStateFlow(prefs.getBoolean("onboarded", existingInstall))
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val _step = MutableStateFlow(prefs.getInt("step",0))
    val step = _step.asStateFlow()
    fun setStep(value: Int) { prefs.edit().putInt("step",value).apply(); _step.value = value }
    val onboarded = _onboarded.asStateFlow()
    fun finishOnboarding() { prefs.edit().putBoolean("onboarded", true).apply(); _onboarded.value = true }
    fun sharedFolder(owner: String): String? = prefs.getString(key(owner,"folder"),null)
    fun setSharedFolder(owner: String, uri: String) { prefs.edit().putString(key(owner,"folder"),uri).apply(); _revision.value++ }
    fun sharing(owner: String) = prefs.getBoolean(key(owner, "sharing"), false)
    fun background(owner: String) = prefs.getBoolean(key(owner, "background"), false)
    fun setSharing(owner: String, enabled: Boolean) { prefs.edit().putBoolean(key(owner, "sharing"), enabled).apply(); _revision.value++ }
    fun setBackground(owner: String, enabled: Boolean) { prefs.edit().putBoolean(key(owner, "background"), enabled).apply(); _revision.value++ }
    private fun key(owner: String, option: String) = SoulseekPolicy.digest(owner) + ":" + option
}
