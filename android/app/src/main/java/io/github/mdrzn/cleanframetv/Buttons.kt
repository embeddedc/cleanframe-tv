package io.github.mdrzn.cleanframetv

/** Pure rules for remote-button assignments (see [KeyRemapService]), kept free of Android types for unit tests. */
object Buttons {
    /**
     * android.view.KeyEvent codes that must keep their job: HOME, BACK, DPAD (including diagonals) and
     * center, ENTER, NUMPAD_ENTER, ESCAPE, volume up/down/mute, MUTE, POWER, TV_POWER, SLEEP and WAKEUP.
     */
    private val RESERVED = setOf(3, 4, 19, 20, 21, 22, 23, 268, 269, 270, 271, 66, 160, 111, 24, 25, 164, 91, 26, 177, 223, 224)

    fun assignable(code: Int): Boolean = code > 0 && code !in RESERVED

    /** A readable name from KeyEvent.keyCodeToString: "KEYCODE_BUTTON_5" → "Button 5"; unknown codes → "Button 192". */
    fun name(code: Int, keyCodeString: String): String {
        val raw = keyCodeString.removePrefix("KEYCODE_")
        if (raw.isEmpty() || raw.all { it.isDigit() }) return "Button $code"
        return raw.lowercase().split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { if (it == "tv") "TV" else it }
            .replaceFirstChar { it.uppercase() }
    }

    /** `{"enabled":…,"buttons":[{"code":192,"name":"Button 5","target":"org.smarttube.stable"}]}`, by keycode. */
    fun listJson(enabled: Boolean, buttons: Map<Int, String>, nameOf: (Int) -> String): String =
        buttons.entries.sortedBy { it.key }.joinToString(",", """{"enabled":$enabled,"buttons":[""", "]}") {
            """{"code":${it.key},"name":${Catalog.quote(nameOf(it.key))},"target":${Catalog.quote(it.value)}}"""
        }

    /** Assigns [target] (an app or input id) to [code], or removes the button when [target] is blank. */
    fun withButton(buttons: Map<Int, String>, code: Int, target: String): Map<Int, String> {
        val out = LinkedHashMap(buttons)
        if (target.isBlank()) out.remove(code) else out[code] = target.trim()
        return out
    }
}
