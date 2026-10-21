package app.runout

import app.runout.soulseek.SoulseekPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Slice0BackupTest {
    @Test fun `legacy cloud backup excludes grant dependent preferences`() {
        val root = xml("backup_rules.xml")
        assertExcluded(root)
    }
    @Test fun `modern cloud backup and device transfer exclude grant dependent preferences`() {
        val root = xml("data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) assertExcluded(root.getElementsByTagName(name).item(0) as Element)
    }
    @Test fun `restored library cache alone cannot skip onboarding`() {
        File(Fixtures.context.filesDir, "library.json").writeText("[]")
        assertFalse(SoulseekPreferences(Fixtures.context).onboarded.value)
    }
    @Test fun `explicit local onboarding remains complete`() {
        SoulseekPreferences(Fixtures.context).finishOnboarding()
        assertTrue(SoulseekPreferences(Fixtures.context).onboarded.value)
    }
    private fun xml(name: String): Element {
        val file = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name")).first { it.exists() }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }
    private fun assertExcluded(root: Element) {
        val elements = root.getElementsByTagName("exclude")
        val paths = (0 until elements.length).map { elements.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }.map { it.getAttribute("path") }
        for (name in listOf("library.xml", "soulseek.xml", "soulseek-setup.xml")) assertTrue("Missing $name in ${root.tagName}", name in paths)
    }
}
