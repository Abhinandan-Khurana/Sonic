package app.runout

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon follows the room's theme: the manifest has two launcher aliases on
 * MainActivity, the cream sleeve (LauncherLight, on by default) and the black one (LauncherDark),
 * and this keeps exactly one of them enabled. MainActivity calls it as the room leaves the screen,
 * so the launcher redraws while nobody's looking and the running room isn't touched.
 */
object LauncherIcon {
    private const val LIGHT = "app.runout.LauncherLight"
    private const val DARK = "app.runout.LauncherDark"

    /** True while the black sleeve is the one on the launcher. */
    fun isDark(context: Context): Boolean = enabled(context, DARK, byDefault = false)

    fun follow(context: Context, dark: Boolean) {
        if (isDark(context) == dark && enabled(context, LIGHT, byDefault = true) == !dark) return
        val pm = context.packageManager
        // The new one first, so there's never a moment with no way into the app.
        pm.setComponentEnabledSetting(component(context, if (dark) DARK else LIGHT), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(component(context, if (dark) LIGHT else DARK), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    private fun enabled(context: Context, name: String, byDefault: Boolean): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> byDefault
            else -> false
        }

    private fun component(context: Context, name: String) = ComponentName(context.packageName, name)
}
