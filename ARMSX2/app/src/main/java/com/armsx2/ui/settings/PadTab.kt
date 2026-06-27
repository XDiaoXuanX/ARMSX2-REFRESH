package com.armsx2.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.config.Settings
import com.armsx2.input.ControllerMappings
import com.armsx2.ui.Colors
import com.armsx2.ui.touch.TouchControls

@Composable
fun PadTab(@Suppress("UNUSED_PARAMETER") state: MutableState<Settings>) {
    val scroll = remember { ScrollState(0) }
    ControllerAutoScroll(scroll)
    val capture = remember { mutableStateOf<ControllerMappings.Action?>(null) }
    val refreshToken = remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val stickCapture = ControllerMappings.captureStickDir
    LaunchedEffect(capture.value, stickCapture.value) {
        // Tell Main.dispatchKeyEvent to stop intercepting controller buttons for
        // overlay nav while we're capturing, so B/A/Y/etc. reach onPreviewKeyEvent
        // and bind instead of (e.g.) exiting the menu. Either an Action capture or
        // a stick-direction capture arms the bypass.
        val capturingNow = capture.value != null || stickCapture.value != null
        ControllerMappings.padCapturing.value = capturingNow
        if (capturingNow)
            focusRequester.requestFocus()
    }
    // Safety: clear the bypass flag if the tab leaves composition mid-capture.
    DisposableEffect(Unit) {
        onDispose {
            ControllerMappings.padCapturing.value = false
            ControllerMappings.captureStickDir.value = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Stick-direction CUSTOM capture: resolve the pressed physical
                // button to the PS2 code it drives (same physical->target lookup
                // as gameplay) and bind that direction. Shares this focusable and
                // the padCapturing bypass with the per-Action capture below.
                val sc = stickCapture.value
                if (sc != null) {
                    if (event.type != KeyEventType.KeyDown)
                        return@onPreviewKeyEvent true
                    val ncode = event.key.nativeKeyCode
                    if (ncode == android.view.KeyEvent.KEYCODE_UNKNOWN)
                        return@onPreviewKeyEvent true
                    val ps2 = ControllerMappings.stickCodeForPhysical(ncode)
                    if (ps2 != null) {
                        ControllerMappings.setCustomStickCode(sc.first, sc.second, ps2)
                        ControllerMappings.endStickCapture()
                        refreshToken.value++
                    }
                    // If the pressed button isn't mapped to any pad Action, keep
                    // waiting (swallow) rather than binding nothing.
                    return@onPreviewKeyEvent true
                }
                // Regular button capture — the menu button is captured in
                // Main.dispatchKeyEvent so it can grab BACK / back-paddle keys.
                val action = capture.value ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown)
                    return@onPreviewKeyEvent true
                val nativeKeyCode = event.key.nativeKeyCode
                if (nativeKeyCode == android.view.KeyEvent.KEYCODE_UNKNOWN)
                    return@onPreviewKeyEvent true
                ControllerMappings.bind(action, nativeKeyCode)
                capture.value = null
                refreshToken.value++
                true
            }
            .verticalScroll(scroll),
    ) {
        Text(
            "Tap an action, then press a physical controller button.",
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        )
        SettingsDivider()
        @Suppress("UNUSED_EXPRESSION")
        refreshToken.value
        // Analog stick remapping — make a physical stick act as the D-pad or the
        // face buttons (great for fighting games on analog-centric controllers).
        run {
            val stickOpts = ControllerMappings.StickMode.values().map { it.label }
            SegmentedRow(
                label = "Left Stick",
                options = stickOpts,
                selectedIndex = ControllerMappings.leftStickMode().ordinal,
                description = "What the left analog stick sends: Analog (default), Face, or Custom (bind each direction below).",
                onChange = {
                    ControllerMappings.setLeftStickMode(ControllerMappings.StickMode.values()[it])
                    refreshToken.value++
                },
            )
            SettingsDivider()
            if (ControllerMappings.leftStickMode() == ControllerMappings.StickMode.CUSTOM) {
                ControllerMappings.StickDir.values().forEach { dir ->
                    StickDirPickerRow(leftStick = true, dir = dir, onChanged = { refreshToken.value++ })
                    SettingsDivider()
                }
            }
            SegmentedRow(
                label = "Right Stick",
                options = stickOpts,
                selectedIndex = ControllerMappings.rightStickMode().ordinal,
                description = "What the right analog stick sends: Analog (default), Face, or Custom (bind each direction below).",
                onChange = {
                    ControllerMappings.setRightStickMode(ControllerMappings.StickMode.values()[it])
                    refreshToken.value++
                },
            )
            SettingsDivider()
            if (ControllerMappings.rightStickMode() == ControllerMappings.StickMode.CUSTOM) {
                ControllerMappings.StickDir.values().forEach { dir ->
                    StickDirPickerRow(leftStick = false, dir = dir, onChanged = { refreshToken.value++ })
                    SettingsDivider()
                }
            }
            ToggleRow(
                "D-pad acts as Left Stick",
                ControllerMappings.dpadAsLeftStick(),
                description = "Make the physical D-pad drive the left analog stick (full deflection) so it works in games that only read the analog stick. The D-pad stops sending digital presses while this is on.",
            ) {
                ControllerMappings.setDpadAsLeftStick(it)
                refreshToken.value++
            }
            SettingsDivider()
        }
        ControllerMappings.actions.forEach { action ->
            val physical = ControllerMappings.physicalFor(action)
            PadBindingRow(
                action = action,
                physical = physical,
                capturing = capture.value == action,
                onClick = { capture.value = action },
            )
            SettingsDivider()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(rowAura())
                .clickable {
                    ControllerMappings.reset()
                    capture.value = null
                    refreshToken.value++
                }
                .controllerFocusable(
                    controllerId = "pad-reset",
                    onConfirm = {
                        ControllerMappings.reset()
                        capture.value = null
                        refreshToken.value++
                    },
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Reset Controller Mappings", color = Colors.pasx2_blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // Controller hotkeys now live in their own dedicated "Hotkeys" tab
        // (see HotkeysTab) so they're easier to find than buried under Pad.
        SettingsDivider()
        IntSliderRow(
            label = "On-screen controls",
            value = TouchControls.visibilityMode.value,
            min = 0,
            max = 11,
            description = "On-screen touch buttons. Never = always hidden (for physical-controls devices — also hides the settings cog so nothing overlaps R1). 1–10s = auto-hide after that long without a touch. Auto = show on touch, hide when you use a controller.",
            valueFormatter = { when (it) { 0 -> "Never"; 11 -> "Auto"; else -> "${it}s" } },
            onChange = { TouchControls.setVisibilityMode(it) },
        )
    }
}

/** One CUSTOM-mode picker row: a stick direction on the left, its bound PS2
 *  button on the right. Tap (or A) arms capture — the row shows yellow
 *  "Press a button..." and the next physical controller button pressed is bound
 *  to this direction (same capture UX as every other binding). D-pad-left clears
 *  the binding back to its analog default. Shown only when the stick is CUSTOM. */
@Composable
private fun StickDirPickerRow(
    leftStick: Boolean,
    dir: ControllerMappings.StickDir,
    onChanged: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    ControllerMappings.stickBindTick.value // recompose after a bind/reset
    val capturing = ControllerMappings.captureStickDir.value == (leftStick to dir)
    val code = ControllerMappings.customStickCode(leftStick, dir)
    fun arm() {
        ControllerMappings.beginStickCapture(leftStick, dir)
        onChanged()
    }
    fun clear() {
        ControllerMappings.resetStickCode(leftStick, dir)
        onChanged()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(rowAura())
            .clickable { arm() }
            .controllerFocusable(
                controllerId = "stickdir:${if (leftStick) "l" else "r"}:${dir.id}",
                onConfirm = { arm() },
                onLeft = { clear() },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "    ${dir.id.replaceFirstChar { it.uppercase() }}",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (capturing) "Press a button..." else ControllerMappings.stickTargetLabel(code),
            color = if (capturing) Color(0xFFFFD33A) else Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PadBindingRow(
    action: ControllerMappings.Action,
    physical: Int,
    capturing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(rowAura())
            .clickable(onClick = onClick)
            .controllerFocusable(
                controllerId = "pad:${action.id}",
                onConfirm = onClick,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(action.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            if (capturing) "Press a button..." else ControllerMappings.labelForKey(physical),
            color = if (capturing) Color(0xFFFFD33A) else Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
