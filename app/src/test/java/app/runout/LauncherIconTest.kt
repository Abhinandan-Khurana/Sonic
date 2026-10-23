package app.runout

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pm = context.packageManager

    private fun launchers() = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        .map { it.activityInfo.name }

    @Test
    fun `the cream sleeve is the launcher icon until the room goes dark, and back`() {
        assertFalse(LauncherIcon.isDark(context))
        assertEquals(listOf("app.runout.LauncherLight"), launchers())

        LauncherIcon.follow(context, dark = true)
        assertTrue(LauncherIcon.isDark(context))
        assertEquals(listOf("app.runout.LauncherDark"), launchers())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(ComponentName(context, "app.runout.LauncherLight")))

        LauncherIcon.follow(context, dark = false)
        assertFalse(LauncherIcon.isDark(context))
        assertEquals(listOf("app.runout.LauncherLight"), launchers())
    }
}
