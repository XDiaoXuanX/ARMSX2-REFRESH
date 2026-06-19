package com.armsx2.input

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import com.armsx2.Main

object ControllerMappings {
    data class Action(
        val id: String,
        val label: String,
        val targetKeyCode: Int,
        val defaultPhysicalKeyCode: Int,
    )

    val actions = listOf(
        Action("dpad_up", "D-Pad Up", KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP),
        Action("dpad_down", "D-Pad Down", KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        Action("dpad_left", "D-Pad Left", KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
        Action("dpad_right", "D-Pad Right", KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
        Action("cross", "Cross", KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A),
        Action("circle", "Circle", KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B),
        Action("square", "Square", KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_X),
        Action("triangle", "Triangle", KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y),
        Action("l1", "L1", KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1),
        Action("r1", "R1", KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1),
        Action("l2", "L2", KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_L2),
        Action("r2", "R2", KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_R2),
        Action("l3", "L3", KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBL),
        Action("r3", "R3", KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_THUMBR),
        Action("select", "Select", KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT),
        Action("start", "Start", KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START),
    )

    private const val KEY_PREFIX = "pad.map."

    fun physicalFor(action: Action): Int =
        Main.prefs.getInt(KEY_PREFIX + action.id, action.defaultPhysicalKeyCode)

    fun labelForKey(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
        KeyEvent.KEYCODE_BUTTON_A -> "Button A"
        KeyEvent.KEYCODE_BUTTON_B -> "Button B"
        KeyEvent.KEYCODE_BUTTON_X -> "Button X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Button Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }

    fun bind(action: Action, physicalKeyCode: Int) {
        Main.prefs.edit().putInt(KEY_PREFIX + action.id, physicalKeyCode).apply()
    }

    fun reset() {
        val edit = Main.prefs.edit()
        actions.forEach { edit.remove(KEY_PREFIX + it.id) }
        edit.apply()
    }

    fun targetForPhysical(physicalKeyCode: Int): Int? =
        actions.firstOrNull { physicalFor(it) == physicalKeyCode }?.targetKeyCode

    // ---- Menu / pause button --------------------------------------------
    // A physical button bound to open the in-game menu (like ARMSX2 OG, and
    // handy for devices with a dedicated menu button, e.g. AYANEO). Stored
    // separately from the emulator-input actions: it isn't forwarded to the
    // PS2, it toggles the overlay. KEYCODE_UNKNOWN = unbound (default).
    private const val KEY_MENU = "pad.menu.keycode"

    fun menuKeyCode(): Int =
        Main.prefs.getInt(KEY_MENU, KeyEvent.KEYCODE_UNKNOWN)

    fun bindMenu(physicalKeyCode: Int) {
        Main.prefs.edit().putInt(KEY_MENU, physicalKeyCode).apply()
    }

    fun clearMenu() {
        Main.prefs.edit().putInt(KEY_MENU, KeyEvent.KEYCODE_UNKNOWN).apply()
    }

    /** True when [physicalKeyCode] is the (set) menu button. */
    fun isMenuKey(physicalKeyCode: Int): Boolean {
        val menu = menuKeyCode()
        return menu != KeyEvent.KEYCODE_UNKNOWN && physicalKeyCode == menu
    }

    // Capture bridge: PadTab sets [menuCaptureActive] = true, then the next key
    // seen by Main.dispatchKeyEvent is bound as the menu button. dispatchKeyEvent
    // is used (not Compose onPreviewKeyEvent) so it can capture KEYCODE_BACK and
    // back-paddle keys, which the back dispatcher would otherwise swallow.
    val menuCaptureActive = mutableStateOf(false)

    /** Bumped after a menu (re)bind so observing UI recomposes. */
    val menuBindTick = mutableStateOf(0)
}
