package io.github.openquesttuner.ui

import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.*
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.ui.components.GameIcon

private val LocalCompactDashboard = staticCompositionLocalOf { false }

private val Orange = Color(0xFFFFA056)

@Composable
fun QuestDashboardScreen(
    model: QuestModel, connectionState: ConnectionState, games: List<InstalledGame>,
    profiles: Map<String, GameProfile>, activeGamePackage: String?, displayRefreshRate: Int?,
    thermalLevel: ThermalLevel, performanceSamples: List<PerformanceSample>, resources: SystemResourceSnapshot,
    onGames: () -> Unit, onProfiles: () -> Unit, onPerformance: () -> Unit,
    onPower: (Boolean) -> Unit, onAccounts: () -> Unit,
    onDevice: () -> Unit, onCommunity: () -> Unit, onSettings: () -> Unit, onOpenGame: (InstalledGame) -> Unit, onAutoTune: (InstalledGame) -> Unit,
) {
    val connected = connectionState is ConnectionState.Connected
    val sample = if (connected) performanceSamples.lastOrNull() else null
    val active = games.firstOrNull { it.packageName == activeGamePackage }
    val profile = activeGamePackage?.let { profiles[it] }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF17263A), Color(0xFF0B1620)), radius = 1600f))) {
        // Fit the normal landscape Quest window. Small/portrait windows retain a
        // scrolling layout so accessibility text and controls remain reachable.
        val wide = maxWidth >= 760.dp && maxHeight >= 504.dp * fontScale && fontScale <= 1.3f
        val dense = maxHeight < 760.dp * fontScale
        if (wide) CompositionLocalProvider(LocalCompactDashboard provides dense) {
            val extra = ((maxHeight - 34.dp) / fontScale - 470.dp).coerceAtLeast(0.dp)
            Row(Modifier.fillMaxSize().padding(if (dense) 8.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(2.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 10.dp)) {
                    DeviceHero(model, connected, displayRefreshRate, sample, if (dense) Modifier.height((136.dp + extra * .25f) * fontScale) else Modifier.weight(1.5f))
                    RecentGames(games, profiles, activeGamePackage, sample, onGames, onOpenGame, if (dense) Modifier.height((138.dp + extra * .6f) * fontScale) else Modifier.weight(1.65f))
                    QuickActions(onGames, { if (active != null) onAutoTune(active) else onGames() }, onDevice, onCommunity, if (dense) Modifier.height((88.dp + extra * .15f) * fontScale) else Modifier.weight(.95f))
                    SystemResources(displayRefreshRate, sample, resources, if (dense) Modifier.weight(1f) else Modifier.weight(.95f))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 10.dp)) {
                    ActiveProfile(active, profile, sample, onOpenGame, onAutoTune, if (dense) Modifier.height(164.dp * fontScale) else Modifier.weight(1.35f))
                    PerformanceStatus(connected, activeGamePackage, thermalLevel, performanceSamples, profile, onPerformance, Modifier.weight(1.45f))
                    ConnectionPanel(connected, onDevice, onPower, onAccounts, if (dense) Modifier.height(152.dp * fontScale) else Modifier.weight(1.65f))
                }
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceHero(model, connected, displayRefreshRate, sample, Modifier, compact = true)
            RecentGames(games, profiles, activeGamePackage, sample, onGames, onOpenGame, Modifier.height(240.dp * fontScale))
            QuickActions(onGames, { if (active != null) onAutoTune(active) else onGames() }, onDevice, onCommunity, Modifier, compact = true)
            ActiveProfile(active, profile, sample, onOpenGame, onAutoTune, Modifier.heightIn(min = 180.dp * fontScale))
            SystemResources(displayRefreshRate, sample, resources, Modifier, compact = true)
            PerformanceStatus(connected, activeGamePackage, thermalLevel, performanceSamples, profile, onPerformance, Modifier.height(245.dp * fontScale))
            ConnectionPanel(connected, onDevice, onPower, onAccounts, Modifier.heightIn(min = 230.dp * fontScale))
        }
    }
    }
}

@Composable
private fun ReferenceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), RoundedCornerShape(13.dp), color = VisionPanel, border = BorderStroke(1.dp, VisionBorder)) {
        Column(Modifier.fillMaxSize().padding(if (LocalCompactDashboard.current) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (LocalCompactDashboard.current) 4.dp else 8.dp), content = content)
    }
}

@Composable
private fun Heading(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun DeviceHero(model: QuestModel, connected: Boolean, rate: Int?, sample: PerformanceSample?, modifier: Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val battery = context.getSystemService(BatteryManager::class.java)
    val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    val minutes = SystemClock.elapsedRealtime() / 60000
    ReferenceCard(modifier) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeadsetPicture(model, Modifier.width(112.dp).height(100.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    ConnectionLabel(connected)
                    Text(percent?.let { "Battery $it%${if (battery?.isCharging == true) " · Charging" else ""}" } ?: "Battery unavailable", fontSize = 12.sp, color = VisionMuted)
                }
            }
            MetricStrip(rate, sample, Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeadsetPicture(model, Modifier.weight(.95f).fillMaxHeight())
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(if (LocalCompactDashboard.current) 2.dp else 5.dp)) {
                Text(model.displayName, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                ConnectionLabel(connected)
                InfoLine("Firmware", Build.DISPLAY)
                InfoLine("Serial", "Unavailable")
                InfoLine("Battery", percent?.let { "$it%${if (battery?.isCharging == true) " · Charging" else ""}" } ?: "—")
                InfoLine("Uptime", "${minutes / 60}h ${minutes % 60}m")
            }
            MetricStrip(rate, sample, Modifier.weight(1.55f))
        }
    }
}

@Composable
private fun HeadsetPicture(model: QuestModel, modifier: Modifier) {
    if (model == QuestModel.QUEST_3) Image(painterResource(R.drawable.quest3_hero), model.displayName, modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { VisionGlyph("device", VisionMuted, Modifier.size(76.dp)) }
}

@Composable
private fun MetricStrip(rate: Int?, sample: PerformanceSample?, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Metric("Refresh Rate", rate?.let { "$it Hz" } ?: "—", VisionBlue, "refresh", Modifier.weight(1f))
        val props = sample?.activeProperties
        val w = props?.get(QuestProperty.TEXTURE_WIDTH.key)?.takeIf(String::isNotBlank)
        val h = props?.get(QuestProperty.TEXTURE_HEIGHT.key)?.takeIf(String::isNotBlank)
        Metric("Resolution", if (w != null && h != null) "$w × $h" else if (sample == null) "—" else "Default", VisionPurple, "resolution", Modifier.weight(1f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Metric("CPU Level", props?.get(QuestProperty.CPU_LEVEL.key)?.takeIf(String::isNotBlank) ?: if (sample == null) "—" else "Auto", VisionGreen, "cpu", Modifier.fillMaxWidth(), small = true)
            Metric("GPU Level", props?.get(QuestProperty.GPU_LEVEL.key)?.takeIf(String::isNotBlank) ?: if (sample == null) "—" else "Auto", Orange, "gpu", Modifier.fillMaxWidth(), small = true)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, kind: String, modifier: Modifier, small: Boolean = false) {
    Surface(modifier, RoundedCornerShape(9.dp), color = Color(0xFF192737), border = BorderStroke(1.dp, color.copy(alpha = .18f))) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = if (LocalCompactDashboard.current) 4.dp else if (small) 7.dp else 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (LocalCompactDashboard.current) 2.dp else 5.dp)) {
            if (!small) VisionGlyph(kind, color, Modifier.size(24.dp))
            Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = Color(0xFFD6E1F3), maxLines = 1)
            Text(value, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = VisionMuted, modifier = Modifier.width(47.dp)); Text(value, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
}

@Composable private fun ConnectionLabel(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(if (connected) VisionGreen else Orange, CircleShape))
        Text(if (connected) "Connected" else "Not connected", fontSize = 12.sp, color = if (connected) VisionGreen else Orange, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentGames(games: List<InstalledGame>, profiles: Map<String, GameProfile>, active: String?, sample: PerformanceSample?, onAll: () -> Unit, open: (InstalledGame) -> Unit, modifier: Modifier) {
    ReferenceCard(modifier) {
        Heading("Recent Games", action = { TextButton(onClick = onAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) { Text("View All", fontSize = 11.sp) } })
        if (games.isEmpty()) {
            Text("No installed games yet. Open the library to refresh.", color = VisionMuted, fontSize = 12.sp)
            TextButton(onClick = onAll) { Text("Open game library") }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tileWidth = ((maxWidth - 32.dp) / 5).coerceIn(92.dp, 190.dp)
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                games.sortedByDescending { it.packageName == active }.take(8).forEach { game ->
                    val running = game.packageName == active
                    val saved = profiles[game.packageName]
                    val applied = running && saved != null && sample != null && saved.isActiveOn(sample.activeProperties)
                    Column(Modifier.width(tileWidth).fillMaxHeight().clickable { open(game) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(10.dp), color = Color(0xFF1C2C43), border = BorderStroke(if (running) 2.dp else 1.dp, if (running) VisionBlue else VisionBorder)) {
                            Box(Modifier.fillMaxSize()) {
                                GameIcon(game.packageName, game.label, Modifier.fillMaxSize(), size = 160.dp)
                                if (running) Icon(Icons.Default.Star, null, tint = Color(0xFFFFD562), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp))
                            }
                        }
                        Text(game.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (running) "● Running" else if (applied) "● Profile Active" else if (saved != null) "● Profile saved" else "● Not Running", fontSize = 10.sp, color = if (running) VisionGreen else if (saved != null) VisionBlue else VisionMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(games: () -> Unit, auto: () -> Unit, device: () -> Unit, community: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    ReferenceCard(modifier) {
        Heading("Quick Actions")
        val actions = listOf(Triple("Browse Games", "Manage profiles\nand settings", games), Triple("Auto Tune", "Optimise for\nbest performance", auto), Triple("Device tools", "Power, tracking\nand terminal", device), Triple("Community", "Download and share\nprofiles", community))
        val colors = listOf(VisionPurple, VisionBlue, Color(0xFF169C53), Color(0xFFE26514))
        if (compact) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            actions.forEachIndexed { i, a -> ActionTile(a.first, a.second, listOf("games","auto","device","profiles")[i], colors[i], a.third, Modifier.weight(1f).widthIn(min = 125.dp).heightIn(min = 86.dp)) }
        } else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, a -> ActionTile(a.first, a.second, listOf("games","auto","device","profiles")[i], colors[i], a.third, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, glyph: String, color: Color, click: () -> Unit, modifier: Modifier) {
    Surface(modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = click), RoundedCornerShape(9.dp), color = color.copy(alpha = .38f), border = BorderStroke(1.dp, color.copy(alpha = .8f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showGlyph = maxWidth >= 140.dp
            val showDescription = maxHeight >= 66.dp
            Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (showGlyph) VisionGlyph(glyph, color = color.copy(red = (color.red + .35f).coerceAtMost(1f), green = (color.green + .35f).coerceAtMost(1f), blue = (color.blue + .35f).coerceAtMost(1f)), Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                    if (showDescription) Text(subtitle, fontSize = 9.sp, lineHeight = 11.sp, color = Color(0xFFCCD9F0))
                }
            }
        }
    }
}

@Composable
private fun SystemResources(rate: Int?, sample: PerformanceSample?, resources: SystemResourceSnapshot, modifier: Modifier, compact: Boolean = false) {
    var details by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val current = resources.fresh(now)
    ReferenceCard(modifier.clickable(onClickLabel = "Show live resource details") { details = true }) {
        if (compact) {
            ResourceRings(current, Modifier.fillMaxWidth())
            HorizontalDivider(color = VisionBorder)
            CurrentSettings(rate, sample, Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ResourceRings(current, Modifier.weight(1.2f))
            VerticalDivider(color = VisionBorder, modifier = Modifier.fillMaxHeight(.85f))
            CurrentSettings(rate, sample, Modifier.weight(1f))
        }
    }
    if (details) ResourceDetails(current) { details = false }
}

@Composable private fun ResourceRings(resources: SystemResourceSnapshot, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("System Resources ⓘ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ResourceRing("GPU", resources.gpu?.value?.roundToInt(), VisionGreen)
            ResourceRing("CPU", resources.cpu?.value?.roundToInt(), VisionBlue)
            ResourceRing("RAM", resources.ramUsedPercent, VisionPurple)
        }
    }
}

@Composable private fun ResourceRing(label: String, value: Int?, color: Color) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(Modifier.size(50.dp * scale).semantics { contentDescription = "$label: ${value?.let { "$it percent" } ?: "unavailable"}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) { drawArc(VisionBorder, -90f, 360f, false, style = Stroke(5.dp.toPx())); if (value != null) drawArc(color, -90f, value.coerceIn(0,100)*3.6f, false, style = Stroke(5.dp.toPx(),cap = StrokeCap.Round)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, fontSize = 10.sp); Text(value?.let { "$it%" } ?: "—", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun CurrentSettings(rate: Int?, sample: PerformanceSample?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Read-back overrides", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SettingLine("Refresh Rate", rate?.let { "$it Hz" } ?: "—")
        SettingLine("CPU Level", sample?.activeProperties?.get(QuestProperty.CPU_LEVEL.key)?.takeIf(String::isNotBlank) ?: if (sample == null) "—" else "Auto")
        SettingLine("GPU Level", sample?.activeProperties?.get(QuestProperty.GPU_LEVEL.key)?.takeIf(String::isNotBlank) ?: if (sample == null) "—" else "Auto")
        val w = sample?.activeProperties?.get(QuestProperty.TEXTURE_WIDTH.key)?.takeIf(String::isNotBlank)
        val h = sample?.activeProperties?.get(QuestProperty.TEXTURE_HEIGHT.key)?.takeIf(String::isNotBlank)
        SettingLine("Resolution", if (w != null && h != null) "$w × $h" else if (sample == null) "—" else "Default")
    }
}
@Composable private fun SettingLine(key: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(key, fontSize = 10.sp, lineHeight = 12.sp, color = VisionMuted, modifier = Modifier.weight(1f)); Text(value, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium) } }

@Composable
private fun ActiveProfile(game: InstalledGame?, profile: GameProfile?, sample: PerformanceSample?, open: (InstalledGame) -> Unit, auto: (InstalledGame) -> Unit, modifier: Modifier) {
    ReferenceCard(modifier) {
        Heading("Active Profile")
        if (game == null) Text("No game is currently running. Choose a game from your library.", color = VisionMuted, fontSize = 12.sp)
        else {
            Surface(Modifier.fillMaxWidth().weight(1f).clickable { open(game) }, RoundedCornerShape(9.dp), color = Color(0xFF192737), border = BorderStroke(1.dp, VisionBorder)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GameIcon(game.packageName, game.label, size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(game.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (profile == null) "Game defaults" else if (sample != null && profile.isActiveOn(sample.activeProperties)) "Profile overrides match" else "Custom profile saved", fontSize = 10.sp, color = VisionMuted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { open(game) }, modifier = Modifier.weight(1f).heightIn(min = 40.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(4.dp)) { Text("Edit Profile", fontSize = 10.sp) }
                Button(onClick = { auto(game) }, modifier = Modifier.weight(1f).heightIn(min = 40.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.buttonColors(containerColor = VisionPurple)) { Text("Auto Tune", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun PerformanceStatus(connected: Boolean, activePackage: String?, thermal: ThermalLevel, samples: List<PerformanceSample>, profile: GameProfile?, open: () -> Unit, modifier: Modifier) {
    val recent = if (connected) samples.filter { it.packageName == activePackage }.takeLast(30) else emptyList()
    val assessment = PerformanceIntelligence.assess(recent, profile)
    val valid = recent.isNotEmpty()
    val stable = valid && assessment.status == PerformanceAssessment.Status.GOOD && !thermal.warning
    val color = if (!valid) VisionMuted else if (stable) VisionGreen else Orange
    ReferenceCard(modifier.clickable(onClick = open)) {
        Heading("Performance Status")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(40.dp), CircleShape, color = color.copy(alpha = .1f), border = BorderStroke(2.dp,color)) { Box(contentAlignment = Alignment.Center) { Text(if (stable) "✓" else if (valid) "!" else "—", color = color, fontSize = 23.sp) } }
            Column(Modifier.weight(1f)) { Text(if (!valid) "Waiting for data" else if (stable) "Stable" else "Needs attention", color = color, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold); Text(if (!valid) "Connect to start monitoring" else "${recent.size} recent samples", fontSize = 10.sp, color = VisionMuted) }
        }
        val timings = recent.map { it.telemetry.measuredFps }
        Surface(Modifier.weight(1f).fillMaxWidth(), RoundedCornerShape(8.dp), color = Color(0xFF0A1620), border = BorderStroke(1.dp,VisionBorder)) {
            if (timings.count { it != null } < 2) Box(contentAlignment = Alignment.Center) { Text("Frame timing unavailable", fontSize = 10.sp, color = VisionMuted) }
            else Canvas(Modifier.fillMaxSize().padding(9.dp).semantics { contentDescription = "Measured VR frame rate from VrApi samples" }) {
                for (i in 1..3) drawLine(VisionBorder, Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f)
                val scale = (recent.mapNotNull { it.refreshRate }.maxOrNull() ?: 120).coerceAtLeast(120).toFloat()
                timings.zipWithNext().forEachIndexed { index, (a,b) -> if (a != null && b != null) drawLine(color,Offset(size.width*index/(timings.size-1),size.height*(1-a.toFloat().coerceIn(0f,scale)/scale)),Offset(size.width*(index+1)/(timings.size-1),size.height*(1-b.toFloat().coerceIn(0f,scale)/scale)),2.dp.toPx(),StrokeCap.Round) }
            }
        }
        Text("VR runtime metrics · details ›", fontSize = 9.sp, color = VisionMuted)
    }
}

@Composable private fun ConnectionPanel(connected: Boolean, open: () -> Unit, power: (Boolean) -> Unit, accounts: () -> Unit, modifier: Modifier) {
    ReferenceCard(modifier) {
        Heading("Headset controls")
        ConnectionLabel(connected)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = open, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("Device tools", fontSize = 10.sp) }
            OutlinedButton(onClick = accounts, enabled = connected, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("Accounts", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { power(true) }, enabled = connected, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("Reboot", fontSize = 10.sp) }
            OutlinedButton(onClick = { power(false) }, enabled = connected, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("Shut down", fontSize = 10.sp) }
        }
    }
}

@Composable private fun ResourceDetails(resources: SystemResourceSnapshot, close: () -> Unit) {
    fun percent(value: ResourceValue?): String = value?.let { String.format(Locale.getDefault(), "%.1f%%", it.value) } ?: "Unavailable"
    fun mb(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.0f MB", it) } ?: "Unavailable"
    AlertDialog(onDismissRequest = close, title = { Text("Live system resources") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CPU mean: ${percent(resources.cpu)}", style = MaterialTheme.typography.titleSmall)
            Text(resources.cpu?.source ?: "Waiting for fresh VrApi or readable Android CPU counters", style = MaterialTheme.typography.bodySmall)
            Text("GPU: ${percent(resources.gpu)}", style = MaterialTheme.typography.titleSmall)
            Text(resources.gpu?.source ?: "No fresh VrApi GPU utilization; no substitute bus metric is used", style = MaterialTheme.typography.bodySmall)
            Text("RAM used: ${resources.ramUsedPercent?.let { "$it%" } ?: "Unavailable"}", style = MaterialTheme.typography.titleSmall)
            Text("Available ${mb(resources.availableRam?.value)} / OS total ${mb(resources.totalRamMb)}", style = MaterialTheme.typography.bodySmall)
            Text(resources.availableRam?.source ?: "Memory counters unavailable", style = MaterialTheme.typography.bodySmall)
            Text("RAM usage is 100 − available / total. Available memory includes reclaimable memory, so this is an estimate rather than a sum of app allocations.", style = MaterialTheme.typography.bodySmall)
            resources.vr?.let { vr ->
                vr.worstCorePercent?.let { Text(String.format(Locale.getDefault(), "CPU busiest core: %.1f%%", it), style = MaterialTheme.typography.bodySmall) }
                Text("Observed levels: CPU ${vr.cpuLevel ?: "—"} / GPU ${vr.gpuLevel ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("Observed clocks: CPU ${vr.cpuClockMhz ?: "—"} / GPU ${vr.gpuClockMhz ?: "—"} MHz", style = MaterialTheme.typography.bodySmall)
            }
            Text("Refreshes about once per second. Counters older than six seconds show unavailable. GPU data may be absent when no VR session is emitting logs.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
}
