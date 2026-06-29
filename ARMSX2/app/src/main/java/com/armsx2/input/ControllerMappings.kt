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
        // The DualShock2 Analog/mode button. Target 200 -> native PAD_ANALOG
        // (see native-lib.cpp setPadButton). UNBOUND by default — there's no
        // standard Android keycode for it, so the user binds a button. Lets the
        // few games that require the analog toggle (e.g. Driving Emotion Type-S)
        // actually enable their sticks. Parity with desktop PCSX2.
        Action("analog", "Analog (toggle)", 200, KeyEvent.KEYCODE_UNKNOWN),
    )

    // ---- Analog stick remapping (physical sticks → digital PS2 inputs) ----
    // Lets a physical stick drive the D-pad or the face buttons instead of the PS2
    // analog stick — handy for fighting games on analog-centric pads (e.g. left
    // stick = D-pad). Global, like the button bindings; persisted in Main.prefs.
    enum class StickMode(val id: String, val label: String) {
        ANALOG("analog", "Analog"),
        FACE("face", "Face"),
        // Per-direction binding: each direction sends any PS2 button (incl. d-pad),
        // captured by "Press a button". This supersedes the old fixed D-Pad preset.
        CUSTOM("custom", "Custom"),
    }

    // ---- Per-player bindings (local co-op) ---------------------------------
    // P1 (0) keeps the existing prefs keys byte-for-byte (empty prefix), so
    // single-player users see ZERO change; P2 (1) namespaces under "p2.". Only
    // WHICH physical button maps to WHICH PS2 button is per-player (button binds,
    // stick mode, custom stick codes). Controller FEEL (sensitivity / accel /
    // deadzone / d-pad-as-left-stick) and SYSTEM hotkeys stay global.
    const val P1 = 0
    const val P2 = 1
    private fun playerPrefix(player: Int) = if (player == 0) "" else "p${player + 1}."

    private const val KEY_LSTICK = "pad.lstick.mode"
    private const val KEY_RSTICK = "pad.rstick.mode"

    private fun stickModeFor(key: String): StickMode {
        val id = Main.prefs.getString(key, StickMode.ANALOG.id)
        return StickMode.values().firstOrNull { it.id == id } ?: StickMode.ANALOG
    }

    fun leftStickMode(player: Int = 0): StickMode = stickModeFor(playerPrefix(player) + KEY_LSTICK)
    fun rightStickMode(player: Int = 0): StickMode = stickModeFor(playerPrefix(player) + KEY_RSTICK)
    fun setLeftStickMode(m: StickMode, player: Int = 0) =
        Main.prefs.edit().putString(playerPrefix(player) + KEY_LSTICK, m.id).apply()
    fun setRightStickMode(m: StickMode, player: Int = 0) =
        Main.prefs.edit().putString(playerPrefix(player) + KEY_RSTICK, m.id).apply()
    /** Mode for the left (true) or right (false) stick — used by the dispatcher. */
    fun stickModeFor(left: Boolean, player: Int = 0): StickMode =
        if (left) leftStickMode(player) else rightStickMode(player)

    // Make the physical D-pad drive the LEFT analog stick (full deflection) so it
    // works in games that only read the analog stick. While on, the D-pad no
    // longer sends digital d-pad presses in-game.
    private const val KEY_DPAD_AS_LSTICK = "pad.dpadAsLeftStick"
    fun dpadAsLeftStick(): Boolean = Main.prefs.getBoolean(KEY_DPAD_AS_LSTICK, false)
    fun setDpadAsLeftStick(on: Boolean) = Main.prefs.edit().putBoolean(KEY_DPAD_AS_LSTICK, on).apply()

    // ---- Analog stick response shaping (physical sticks → PS2 analog) ----
    // Sensitivity = a linear output scale. Acceleration = an exponential response
    // curve applied to the post-deadzone magnitude (0 = linear; higher = finer
    // control near center, ramping to full speed at full tilt). Global controller
    // feel, persisted in Main.prefs; cached so the hot motion path avoids a lookup.
    private const val KEY_STICK_SENS = "pad.stick.sensitivity"
    private const val KEY_STICK_ACCEL = "pad.stick.acceleration"
    const val STICK_SENS_MIN = 0.5f
    const val STICK_SENS_MAX = 2.0f
    const val STICK_ACCEL_MAX = 2.0f
    @Volatile private var sStickSens = Float.NaN
    @Volatile private var sStickAccel = Float.NaN
    fun stickSensitivity(): Float {
        if (sStickSens.isNaN())
            sStickSens = Main.prefs.getFloat(KEY_STICK_SENS, 1.0f).coerceIn(STICK_SENS_MIN, STICK_SENS_MAX)
        return sStickSens
    }
    fun setStickSensitivity(v: Float) {
        val c = v.coerceIn(STICK_SENS_MIN, STICK_SENS_MAX)
        sStickSens = c
        Main.prefs.edit().putFloat(KEY_STICK_SENS, c).apply()
    }
    fun stickAcceleration(): Float {
        if (sStickAccel.isNaN())
            sStickAccel = Main.prefs.getFloat(KEY_STICK_ACCEL, 0.0f).coerceIn(0f, STICK_ACCEL_MAX)
        return sStickAccel
    }
    fun setStickAcceleration(v: Float) {
        val c = v.coerceIn(0f, STICK_ACCEL_MAX)
        sStickAccel = c
        Main.prefs.edit().putFloat(KEY_STICK_ACCEL, c).apply()
    }

    // App-side analog stick deadzone (fraction of travel ignored). Kept small by
    // default and user-adjustable down to 0 — handheld "switch" sticks have tiny
    // range, so a big deadzone wastes most of it. Output is re-normalized past the
    // deadzone (see Main.shapeStickMag) so movement ramps smoothly from 0 instead
    // of jumping. Pairs with forcing the NATIVE pad deadzone to 0.
    private const val KEY_STICK_DZ = "pad.stick.deadzone"
    const val STICK_DZ_MAX = 0.40f
    @Volatile private var sStickDz = Float.NaN
    fun stickDeadzone(): Float {
        if (sStickDz.isNaN())
            sStickDz = Main.prefs.getFloat(KEY_STICK_DZ, 0.05f).coerceIn(0f, STICK_DZ_MAX)
        return sStickDz
    }
    fun setStickDeadzone(v: Float) {
        val c = v.coerceIn(0f, STICK_DZ_MAX)
        sStickDz = c
        Main.prefs.edit().putFloat(KEY_STICK_DZ, c).apply()
    }

    // Outer (anti-)deadzone: fraction of travel near the EDGE that maps to full
    // output, so a stick that can't physically reach its corners still hits 100%
    // (short-throw / handheld sticks like the AYN Odin). 0 = off. Applied in
    // Main.shapeStickMag as the upper edge of the post-deadzone re-normalize window.
    private const val KEY_STICK_OUTER = "pad.stick.outerDeadzone"
    const val STICK_OUTER_MAX = 0.40f
    @Volatile private var sStickOuter = Float.NaN
    fun stickOuterDeadzone(): Float {
        if (sStickOuter.isNaN())
            sStickOuter = Main.prefs.getFloat(KEY_STICK_OUTER, 0.0f).coerceIn(0f, STICK_OUTER_MAX)
        return sStickOuter
    }
    fun setStickOuterDeadzone(v: Float) {
        val c = v.coerceIn(0f, STICK_OUTER_MAX)
        sStickOuter = c
        Main.prefs.edit().putFloat(KEY_STICK_OUTER, c).apply()
    }

    // ---- Custom per-direction stick→button binding (StickMode.CUSTOM) ----

    /** The four directions of a stick, each independently bindable in CUSTOM mode. */
    enum class StickDir(val id: String) { UP("up"), DOWN("down"), LEFT("left"), RIGHT("right") }

    /** A PS2 button a stick direction may map to (digital setPadButton codes from
     *  native-lib.cpp). [label] drives the picker UI. */
    data class PsButton(val code: Int, val label: String)
    val stickTargets = listOf(
        PsButton(19, "D-Pad Up"), PsButton(20, "D-Pad Down"),
        PsButton(21, "D-Pad Left"), PsButton(22, "D-Pad Right"),
        PsButton(96, "Cross"), PsButton(97, "Circle"),
        PsButton(99, "Square"), PsButton(100, "Triangle"),
        PsButton(102, "L1"), PsButton(103, "R1"),
        PsButton(104, "L2"), PsButton(105, "R2"),
        PsButton(106, "L3"), PsButton(107, "R3"),
        PsButton(108, "Start"), PsButton(109, "Select"),
        PsButton(200, "Analog (toggle)"),
    )
    fun stickTargetLabel(code: Int): String =
        stickTargets.firstOrNull { it.code == code }?.label ?: "Code $code"

    // Default per-direction code = the stick's native analog code, so a fresh
    // CUSTOM stick behaves exactly like ANALOG until the user rebinds a direction.
    private fun defaultCustomCode(left: Boolean, dir: StickDir): Int = when {
        left && dir == StickDir.UP -> 110
        left && dir == StickDir.DOWN -> 112
        left && dir == StickDir.LEFT -> 113
        left && dir == StickDir.RIGHT -> 111
        !left && dir == StickDir.UP -> 120
        !left && dir == StickDir.DOWN -> 122
        !left && dir == StickDir.LEFT -> 123
        else -> 121 // right stick, RIGHT
    }
    private fun customKey(left: Boolean, dir: StickDir, player: Int = 0) =
        playerPrefix(player) + "pad.${if (left) "lstick" else "rstick"}.${dir.id}.code"
    fun customStickCode(left: Boolean, dir: StickDir, player: Int = 0): Int =
        Main.prefs.getInt(customKey(left, dir, player), defaultCustomCode(left, dir))
    fun setCustomStickCode(left: Boolean, dir: StickDir, code: Int, player: Int = 0) =
        Main.prefs.edit().putInt(customKey(left, dir, player), code).apply()

    /** Active CUSTOM stick-direction capture target, or null. (left, dir). When
     *  non-null the Pad tab is waiting for a physical button to bind to this
     *  direction — same model as [captureHotkey] / the per-Action [padCapturing]
     *  flow. Observed by the row UI for the yellow "Press a button..." text. */
    val captureStickDir = mutableStateOf<Pair<Boolean, StickDir>?>(null)

    /** Bumped after a stick-dir (re)bind so the observing row recomposes. */
    val stickBindTick = mutableStateOf(0)

    /** Resolve a captured physical keycode to the PS2 setPadButton code it drives,
     *  or null if that physical button isn't bound to any pad Action. Same
     *  physical->target lookup the gameplay path uses, e.g. physical-Cross -> 96.
     *  Reusing it means "stick Up = Cross" needs no new table. */
    fun stickCodeForPhysical(physicalKeyCode: Int, player: Int = 0): Int? =
        targetForPhysical(physicalKeyCode, player)

    fun beginStickCapture(left: Boolean, dir: StickDir) { captureStickDir.value = left to dir }
    fun endStickCapture() { captureStickDir.value = null; stickBindTick.value++ }

    /** Clear a direction back to its analog default (the Reset affordance). */
    fun resetStickCode(left: Boolean, dir: StickDir, player: Int = 0) {
        Main.prefs.edit().remove(customKey(left, dir, player)).apply(); stickBindTick.value++
    }

    private const val KEY_PREFIX = "pad.map."

    fun physicalFor(action: Action, player: Int = 0): Int =
        Main.prefs.getInt(playerPrefix(player) + KEY_PREFIX + action.id, action.defaultPhysicalKeyCode)

    fun labelForKey(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_UNKNOWN -> "Not set"
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

    fun bind(action: Action, physicalKeyCode: Int, player: Int = 0) {
        Main.prefs.edit().putInt(playerPrefix(player) + KEY_PREFIX + action.id, physicalKeyCode).apply()
    }

    fun reset(player: Int = 0) {
        val edit = Main.prefs.edit()
        actions.forEach { edit.remove(playerPrefix(player) + KEY_PREFIX + it.id) }
        edit.apply()
    }

    /** Reset the pad TUNABLES to defaults for the global "Reset to defaults" — stick
     *  feel (deadzone/sensitivity/acceleration), D-pad-as-left-stick, stick modes and
     *  CUSTOM stick-direction codes, for BOTH players. Does NOT touch the button binds
     *  (those have their own per-player Reset). Bumps stickBindTick so the Pad tab
     *  recomposes. (The button-bind sliders live outside the Settings object, which is
     *  why the Settings reset alone didn't clear them.) */
    fun resetTunables() {
        val edit = Main.prefs.edit()
        edit.remove(KEY_STICK_SENS).remove(KEY_STICK_ACCEL).remove(KEY_STICK_DZ)
            .remove(KEY_STICK_OUTER).remove(KEY_DPAD_AS_LSTICK)
        sStickSens = Float.NaN; sStickAccel = Float.NaN; sStickDz = Float.NaN; sStickOuter = Float.NaN
        for (p in intArrayOf(P1, P2)) {
            edit.remove(playerPrefix(p) + KEY_LSTICK).remove(playerPrefix(p) + KEY_RSTICK)
            for (left in booleanArrayOf(true, false))
                for (dir in StickDir.values())
                    edit.remove(customKey(left, dir, p))
        }
        edit.apply()
        stickBindTick.value++
    }

    fun targetForPhysical(physicalKeyCode: Int, player: Int = 0): Int? =
        actions.firstOrNull { physicalFor(it, player) == physicalKeyCode }?.targetKeyCode

    // ---- System hotkeys (menu / quick save / quick load) -----------------
    // Physical buttons bound to app actions, NOT forwarded to the PS2. Handled in
    // Main.dispatchKeyEvent (so they can catch KEYCODE_BACK / back-paddle keys the
    // back dispatcher would otherwise swallow). KEYCODE_UNKNOWN = unbound.
    enum class SysHotkey(val prefKey: String, val label: String) {
        MENU("pad.menu.keycode", "Menu / Pause"),
        SAVE_STATE("pad.savestate.keycode", "Quick Save State"),
        LOAD_STATE("pad.loadstate.keycode", "Quick Load State"),
        CYCLE_SLOT("pad.cycleslot.keycode", "Cycle Save Slot"),
        TEXTURE_DUMP("pad.texdump.keycode", "Toggle Texture Dumping"),
        FAST_FORWARD("pad.fastforward.keycode", "Fast Forward (hold)"),
        FAST_FORWARD_TOGGLE("pad.fastforwardtoggle.keycode", "Fast Forward (toggle)"),
        RES_UP("pad.resup.keycode", "Increase Resolution"),
        RES_DOWN("pad.resdown.keycode", "Decrease Resolution"),
        ACHIEVEMENTS("pad.achievements.keycode", "Open Achievements"),
        CLOSE_GAME("pad.closegame.keycode", "Close Game"),
        QUIT_APP("pad.quitapp.keycode", "Close Game & Quit"),
        // Hold-type binding: while the bound button is held, pressure-capable PS2
        // buttons report a soft (~50%) press. Handled as a HOLD in
        // Main.dispatchKeyEvent (sets TouchControls.pressureModifierHeld), not as a
        // one-shot action like the others.
        PRESSURE_MOD("pad.pressuremod.keycode", "Pressure Modifier (hold)"),
    }

    // A hotkey is either a single button or a two-button combo. The main key is
    // stored under prefKey; an optional modifier (held while the main key is
    // pressed) under prefKey + MOD_SUFFIX. UNKNOWN modifier = single-button.
    private const val MOD_SUFFIX = ".mod"

    fun hotkeyCode(h: SysHotkey): Int =
        Main.prefs.getInt(h.prefKey, KeyEvent.KEYCODE_UNKNOWN)

    /** Modifier button that must be held with [hotkeyCode], or UNKNOWN for none. */
    fun hotkeyModCode(h: SysHotkey): Int =
        Main.prefs.getInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)

    /** Bind a single-button hotkey (clears any modifier). */
    fun bindHotkey(h: SysHotkey, physicalKeyCode: Int) {
        Main.prefs.edit()
            .putInt(h.prefKey, physicalKeyCode)
            .putInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
            .apply()
    }

    /** Bind a two-button combo: [modCode] held + [keyCode] pressed. */
    fun bindHotkeyCombo(h: SysHotkey, modCode: Int, keyCode: Int) {
        Main.prefs.edit()
            .putInt(h.prefKey, keyCode)
            .putInt(h.prefKey + MOD_SUFFIX, modCode)
            .apply()
    }

    fun clearHotkey(h: SysHotkey) {
        Main.prefs.edit()
            .putInt(h.prefKey, KeyEvent.KEYCODE_UNKNOWN)
            .putInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
            .apply()
    }

    /** Clear ALL system hotkey bindings (the global "Reset to defaults"). Bumps
     *  hotkeyBindTick so the Hotkeys tab recomposes. */
    fun clearAllHotkeys() {
        val edit = Main.prefs.edit()
        SysHotkey.values().forEach {
            edit.putInt(it.prefKey, KeyEvent.KEYCODE_UNKNOWN)
                .putInt(it.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
        }
        edit.apply()
        hotkeyBindTick.value++
    }

    /** Human-readable binding, e.g. "Select + R1" or "L1", or "" if unbound. */
    fun hotkeyLabel(h: SysHotkey): String {
        val key = hotkeyCode(h)
        if (key == KeyEvent.KEYCODE_UNKNOWN) return ""
        val mod = hotkeyModCode(h)
        return if (mod == KeyEvent.KEYCODE_UNKNOWN) labelForKey(key)
        else "${labelForKey(mod)} + ${labelForKey(key)}"
    }

    /** Single-button match (combos excluded). Used by the frontend MENU shortcut. */
    fun hotkeyFor(physicalKeyCode: Int): SysHotkey? {
        if (physicalKeyCode == KeyEvent.KEYCODE_UNKNOWN) return null
        return SysHotkey.values().firstOrNull {
            hotkeyCode(it) == physicalKeyCode && hotkeyModCode(it) == KeyEvent.KEYCODE_UNKNOWN
        }
    }

    /** Combo-aware match for the just-pressed [keyCode] given the set of
     *  currently-held physical keys. Combos (modifier held) win over a plain
     *  single binding on the same key, so e.g. Select+R1 fires its action
     *  instead of a bare-R1 binding while Select is held. */
    fun matchHotkey(keyCode: Int, heldKeys: Set<Int>): SysHotkey? {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return null
        SysHotkey.values().firstOrNull {
            hotkeyCode(it) == keyCode &&
                hotkeyModCode(it) != KeyEvent.KEYCODE_UNKNOWN &&
                heldKeys.contains(hotkeyModCode(it))
        }?.let { return it }
        return SysHotkey.values().firstOrNull {
            hotkeyCode(it) == keyCode && hotkeyModCode(it) == KeyEvent.KEYCODE_UNKNOWN
        }
    }

    // True while the Pad tab is waiting for a button to bind. Main.dispatchKeyEvent
    // checks this and lets EVERY key fall through to Compose's onPreviewKeyEvent so
    // any button — including B/A/Y/D-pad/L1/R1 the overlay nav would otherwise
    // consume (B = exit) — can be captured. Normal nav resumes when it clears.
    val padCapturing = mutableStateOf(false)

    // Capture bridge: the Hotkeys tab calls [beginHotkeyCapture]; the next
    // button(s) seen by Main.dispatchKeyEvent are bound to it. Press one button
    // for a single bind, or two together for a combo. Observed for UI feedback.
    val captureHotkey = mutableStateOf<SysHotkey?>(null)

    /** Ordered buffer of buttons pressed during an active capture (≤2 used). */
    val captureKeys = mutableListOf<Int>()

    /** Start capturing a (re)binding for [h]. */
    fun beginHotkeyCapture(h: SysHotkey) {
        captureKeys.clear()
        captureHotkey.value = h
    }

    /** End the current capture session. */
    fun endHotkeyCapture() {
        captureKeys.clear()
        captureHotkey.value = null
        hotkeyBindTick.value++
    }

    /** Bumped after a (re)bind so observing UI recomposes. */
    val hotkeyBindTick = mutableStateOf(0)
}
