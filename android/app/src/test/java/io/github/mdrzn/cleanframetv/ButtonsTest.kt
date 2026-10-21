package io.github.mdrzn.cleanframetv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonsTest {
    @Test fun navigationAndSystemKeysCannotBeAssigned() {
        // HOME, BACK, DPAD up/down/left/right/center, ENTER, ESCAPE, VOLUME up/down/mute, MUTE, POWER, TV_POWER, SLEEP, WAKEUP
        listOf(3, 4, 19, 20, 21, 22, 23, 66, 111, 24, 25, 164, 91, 26, 177, 223, 224).forEach { assertFalse("$it", Buttons.assignable(it)) }
        listOf(192, 193, 82, 176, 183).forEach { assertTrue("$it", Buttons.assignable(it)) }
        assertFalse(Buttons.assignable(0))
    }

    @Test fun keyNamesAreReadable() {
        assertEquals("Button 5", Buttons.name(192, "KEYCODE_BUTTON_5"))
        assertEquals("Prog red", Buttons.name(183, "KEYCODE_PROG_RED"))
        assertEquals("TV input", Buttons.name(178, "KEYCODE_TV_INPUT"))
        assertEquals("Button 700", Buttons.name(700, "700"))
        assertEquals("Button 701", Buttons.name(701, ""))
    }

    @Test fun listJsonIsSortedAndEscaped() {
        val json = JSONObject(Buttons.listJson(true, mapOf(193 to "org.jellyfin.androidtv", 192 to "input.hw5")) { "Key \"$it\"" })
        assertTrue(json.getBoolean("enabled"))
        val list = json.getJSONArray("buttons")
        assertEquals(2, list.length())
        assertEquals(192, list.getJSONObject(0).getInt("code"))
        assertEquals("Key \"192\"", list.getJSONObject(0).getString("name"))
        assertEquals("input.hw5", list.getJSONObject(0).getString("target"))
        assertEquals("org.jellyfin.androidtv", list.getJSONObject(1).getString("target"))
        assertFalse(JSONObject(Buttons.listJson(false, emptyMap()) { "" }).getBoolean("enabled"))
    }

    @Test fun assigningReplacesAndBlankRemoves() {
        val start = mapOf(192 to "org.smarttube.stable")
        assertEquals(mapOf(192 to "org.smarttube.stable", 193 to "org.jellyfin.androidtv"), Buttons.withButton(start, 193, "org.jellyfin.androidtv"))
        assertEquals(mapOf(192 to "input.hw5"), Buttons.withButton(start, 192, " input.hw5 "))
        assertEquals(emptyMap<Int, String>(), Buttons.withButton(start, 192, ""))
    }
}
