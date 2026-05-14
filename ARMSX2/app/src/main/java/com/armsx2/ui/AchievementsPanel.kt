package com.armsx2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONObject

/**
 * Achievements list shown in the right column of the in-game overlay.
 *
 * Snapshots [NativeApp.getAchievementsJSON] when the panel composes and
 * polls every few seconds while open so freshly-unlocked entries surface
 * without the user closing/reopening the overlay. Style mirrors the
 * setup-wizard BIOS bubble — rounded card with an icon column and
 * title/description stack — for visual consistency.
 *
 * Empty / no-game / not-logged-in states render a single status card
 * instead of a hard hide, so the column doesn't visually disappear and
 * confuse users who expect achievements to always show up.
 */
data class AchievementSnapshot(
    val items: List<Achievement>,
    val active: Boolean,
    val loggedIn: Boolean,
    val hardcore: Boolean,
    val userName: String,
)

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val bucket: Int,
    val rarity: Float,
    val measuredProgress: String,
)

private fun parseSnapshot(json: String): AchievementSnapshot {
    return try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("items")
        val items = if (arr == null) emptyList() else List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Achievement(
                id = o.optInt("id", 0),
                title = o.optString("title", ""),
                description = o.optString("description", ""),
                points = o.optInt("points", 0),
                unlocked = o.optBoolean("unlocked", false),
                bucket = o.optInt("bucket", -1),
                rarity = o.optDouble("rarity", 0.0).toFloat(),
                measuredProgress = o.optString("measuredProgress", ""),
            )
        }
        AchievementSnapshot(
            items = items,
            active = root.optBoolean("active", false),
            loggedIn = root.optBoolean("loggedIn", false),
            hardcore = root.optBoolean("hardcore", false),
            userName = root.optString("userName", ""),
        )
    } catch (_: Exception) {
        AchievementSnapshot(emptyList(), active = false, loggedIn = false,
            hardcore = false, userName = "")
    }
}

@Composable
fun AchievementsPanel(
    modifier: Modifier = Modifier,
    onSignInClick: () -> Unit = {},
    onHardcoreToggle: (() -> Unit)? = null,
) {
    var snapshot by remember {
        mutableStateOf(AchievementSnapshot(emptyList(), false, false, false, ""))
    }

    // Poll on open + every 4s while the composable is alive (overlay
    // open). Achievements::GetAchievementsAsJSON locks rcheevos +
    // re-creates the bucket list each call, so cap the rate. JNI string
    // marshalling on the Main thread can stutter the overlay if the list
    // is large, so dispatch on IO and assign back via setState.
    LaunchedEffect(Unit) {
        while (true) {
            val json = withContext(Dispatchers.IO) {
                runCatching { NativeApp.getAchievementsJSON() }.getOrNull() ?: ""
            }
            val s = parseSnapshot(json)
            snapshot = s
            // Mirror the live hardcore flag to the overlay-level state
            // that drives Save / Load State row dimming. Doing it here so
            // we don't add a second polling loop.
            InGameOverlay.hardcoreOn.value = s.hardcore
            delay(4000)
        }
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Achievements",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Hardcore toggle button. Greyed when off, red when on. Tap
            // routes to the host (overlay) which shows a fullscreen
            // confirmation before flipping the flag — enabling hardcore
            // resets the running VM, so we want a deliberate action.
            @Suppress("KotlinConstantConditions")
            if (false && onHardcoreToggle != null) {
                val active = snapshot.hardcore
                val bg = if (active) Color(0xFFB22222) else Color(0xFF333333)
                val fg = if (active) Color.White else Color(0xFF888888)
                val border = if (active) Color(0xFFFF6B6B) else Color(0xFF555555)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(6.dp))
                        .clickable(onClick = onHardcoreToggle!!)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "HARDCORE",
                        color = fg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        when {
            !snapshot.loggedIn -> StatusCard(
                title = "Not signed in",
                body = "Tap to sign in to RetroAchievements and track unlocks for the games you play.",
                onClick = onSignInClick,
            )
            !snapshot.active -> StatusCard(
                title = "No achievements",
                body = "This game has no RetroAchievements set, or the title isn't recognised.",
            )
            snapshot.items.isEmpty() -> StatusCard(
                title = "Loading…",
                body = "Fetching achievement list.",
            )
            else -> {
                // Username + logout button on the same row when signed in.
                // Logout calls Achievements::Logout via JNI which clears the
                // SECRETS-layer Token; the next AchievementsPanel poll picks
                // up loggedIn=false and the panel reverts to the "Not signed
                // in" StatusCard automatically — no local state to reset.
                if (snapshot.userName.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = snapshot.userName,
                            color = Color(0xFFAACCFF),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Logout",
                            color = Color(0xFFFF8888),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    runCatching { NativeApp.logoutAchievements() }
                                    // Snapshot will refresh on the next poll
                                    // (≤ 4s); render immediate feedback now.
                                    snapshot = AchievementSnapshot(
                                        emptyList(), active = false,
                                        loggedIn = false, hardcore = false,
                                        userName = ""
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                val unlocked = snapshot.items.count { it.unlocked }
                Text(
                    text = "$unlocked / ${snapshot.items.size} unlocked",
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(snapshot.items, key = { it.id }) { ach ->
                        AchievementRow(ach)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF333333))
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = withClick.padding(12.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color(0xFFCCCCCC), fontSize = 12.sp)
    }
}

@Composable
private fun AchievementRow(ach: Achievement) {
    val bg = if (ach.unlocked) Colors.pasx2_blue.copy(alpha = 0.30f) else Color(0xFF333333)
    val border = if (ach.unlocked) Colors.pasx2_blue else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // State glyph + points badge column. ✓ for unlocked, lock for
        // locked, hourglass for in-progress measured items.
        val glyph = when {
            ach.unlocked -> "✓"
            ach.measuredProgress.isNotEmpty() -> "⏳"
            else -> "🔒"
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                glyph,
                fontSize = 18.sp,
                color = if (ach.unlocked) Colors.pasx2_blue else Color(0xFFAAAAAA),
            )
            if (ach.points > 0) {
                Text(
                    "${ach.points}",
                    color = Color(0xFFFFCC66),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                ach.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ach.description.isNotEmpty()) {
                Text(
                    ach.description,
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ach.measuredProgress.isNotEmpty() && !ach.unlocked) {
                Spacer(Modifier.height(2.dp))
                Text(
                    ach.measuredProgress,
                    color = Color(0xFFAACCFF),
                    fontSize = 10.sp,
                )
            }
        }
    }
}
