package io.github.mdrzn.cleanframetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private val self = "io.github.mdrzn.cleanframetv"

    @Test fun appsAreDedupedSortedAndExcludeSelf() {
        val apps = Catalog.apps(
            listOf(
                AppCandidate("org.smarttube.stable", "SmartTube", leanback = true),
                AppCandidate("com.netflix.ninja", "Netflix", leanback = true),
                AppCandidate("org.smarttube.stable", "SmartTube (phone)", leanback = false),
                AppCandidate(self, "CleanFrame TV", leanback = true),
                AppCandidate("com.example.phone", "Phone only", leanback = false),
            ),
            self,
        )
        assertEquals(listOf("com.netflix.ninja", "com.example.phone", "org.smarttube.stable"), apps.map { it.appId }) // by name
        assertEquals("SmartTube", apps.first { it.appId == "org.smarttube.stable" }.name) // leanback label wins
        assertEquals("/icons/com.netflix.ninja.png", apps.first { it.appId == "com.netflix.ninja" }.icon)
        assertTrue(apps.all { it.kind == "app" })
    }

    @Test fun onlyHdmiPortsBecomeInputs() {
        val inputs = Catalog.inputs(
            listOf(
                InputCandidate("com.mediatek.tvinput/.hdmi.HDMIInputService/HW4", Catalog.TYPE_HDMI, "HDMI 1", null),
                InputCandidate("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", Catalog.TYPE_HDMI, "HDMI 2", null),
                InputCandidate("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5/cec4", Catalog.TYPE_HDMI, "Switch (CEC)", "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"),
                InputCandidate("com.mediatek.tvinput/.tuner.TunerInputService/HW0", 0, "Antenna", null),
                InputCandidate("com.mediatek.tvinput/.composite.CompositeInputService/HW1", 1001, "AV", null),
            ),
            mapOf("input.hw5" to "Nintendo Switch"),
        )
        assertEquals(listOf("input.hw4", "input.hw5"), inputs.map { it.entry.appId })
        assertEquals(listOf("HDMI 1", "Nintendo Switch"), inputs.map { it.entry.name })
        assertEquals("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", inputs[1].inputId)
        assertTrue(inputs.all { it.entry.kind == "input" && it.entry.icon == "" })
    }

    @Test fun inputIdsAreSafeForTheWebInterface() {
        assertEquals("input.hw6", Catalog.inputAppId("com.mediatek.tvinput/.hdmi.HDMIInputService/HW6"))
        assertEquals("input.hdmi_2", Catalog.inputAppId("com.example.tv/.Hdmi/HDMI 2"))
        assertFalse(Catalog.inputAppId("x/../y").contains("/"))
    }

    @Test fun jsonEscapesNamesSafely() {
        val json = Catalog.toJson(listOf(CatalogEntry("a.b", "Quote \" and \\ and \n line", "app", "/icons/a.b.png")))
        assertEquals("""[{"appId":"a.b","name":"Quote \" and \\ and \n line","kind":"app","icon":"/icons/a.b.png"}]""", json)
    }

    @Test fun configSplitsInputNamesFromWebPreferences() {
        val (web, names) = Catalog.splitConfig("""{"home":["primevideo","input.hw5"],"inputNames":{"input.hw5":"Nintendo Switch","input.hw6":"PC"},"clock":"24"}""")
        assertEquals(mapOf("input.hw5" to "Nintendo Switch", "input.hw6" to "PC"), names)
        assertFalse(web.contains("inputNames"))
        assertTrue(web.contains("\"clock\":\"24\""))
    }
}
