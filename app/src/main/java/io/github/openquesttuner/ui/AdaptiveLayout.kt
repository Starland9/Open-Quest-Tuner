package io.github.openquesttuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.openquesttuner.BuildConfig
import io.github.openquesttuner.core.QuestModel

val VisionBlue = Color(0xFF4285FF)
val VisionPurple = Color(0xFF8536F4)
val VisionGreen = Color(0xFF58E89B)
val VisionMuted = Color(0xFFAABCD7)
val VisionBorder = Color(0xFF24364B)
val VisionPanel = Color(0xFF101C29)

private data class Destination(val title: String, val icon: ImageVector, val screen: Screen)
private val destinations = listOf(
    Destination("Home", Icons.Default.Home, Screen.Dashboard),
    Destination("Games", Icons.Default.Star, Screen.Games),
    Destination("Profiles", Icons.Default.Edit, Screen.GlobalProfile),
    Destination("Performance", Icons.Default.Info, Screen.PerformanceMonitor),
    Destination("Device", Icons.Default.Settings, Screen.DeviceTools),
    Destination("Community", Icons.Default.Person, Screen.CatalogUpdates),
    Destination("Settings", Icons.Default.Settings, Screen.Settings),
)

@Composable
fun AdaptiveAppShell(current: Screen, onNavigate: (Screen) -> Unit, model: QuestModel, connected: Boolean, content: @Composable () -> Unit) {
    if (current == Screen.Onboarding) { content(); return }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0A131D))) {
        val viewportWidth = maxWidth
        val sidebar = maxWidth >= 900.dp
        val rail = maxWidth >= 680.dp
        val compact = maxWidth < 680.dp
        val selected = if (current is Screen.Profile) Screen.Games else current
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF111B2A), Color(0xFF182231)))).padding(horizontal = if (compact) 12.dp else 20.dp).heightIn(min = if (compact) 48.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
                ApertureMark(Modifier.size(if (compact) 26.dp else 28.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (compact) "Open Quest Tuner" else "Open Quest Tuner Pro", fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!compact) Text("v${BuildConfig.VERSION_NAME.substringBefore('-')}", color = VisionMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0B1421)) {
                    Row(Modifier.clickable { onNavigate(Screen.Connection) }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!compact) { VisionGlyph("device", VisionMuted, Modifier.size(22.dp)); Text(model.displayName, fontSize = 12.sp) }
                        Box(Modifier.size(8.dp).background(if (connected) VisionGreen else Color(0xFFFFAC67), CircleShape))
                        Text(if (connected) "Connected" else "Offline", color = if (connected) VisionGreen else Color(0xFFFFAC67), fontSize = 12.sp)
                    }
                }

            }
            HorizontalDivider(color = VisionBorder)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (rail) {
                    Column(Modifier.width(if (sidebar) if (viewportWidth >= 1300.dp) 164.dp else 140.dp else 72.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF0E1721), Color(0xFF09121B)))).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        destinations.forEach { dest ->
                            val active = selected == dest.screen
                            val background = if (active) Brush.horizontalGradient(listOf(Color(0xFF4318CF), Color(0xFF0864CA))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            if (sidebar) Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(9.dp)).background(background).clickable { onNavigate(dest.screen) }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (dest.title in listOf("Games", "Performance", "Device", "Profiles")) VisionGlyph(dest.title.lowercase(), if (active) Color.White else VisionMuted, Modifier.size(20.dp)) else Icon(dest.icon, null, tint = if (active) Color.White else VisionMuted)
                                Text(dest.title, fontSize = 12.sp, color = if (active) Color.White else VisionMuted)
                            } else NavigationRailItem(selected = active, onClick = { onNavigate(dest.screen) }, icon = { Icon(dest.icon, null) }, label = { Text(dest.title, fontSize = 10.sp) })
                        }
                    }
                    VerticalDivider(color = VisionBorder)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
            if (compact) {
                var more by remember { mutableStateOf(false) }
                NavigationBar(containerColor = Color(0xFF111D2B)) {
                    destinations.take(3).forEach { dest -> NavigationBarItem(selected = selected == dest.screen, onClick = { onNavigate(dest.screen) }, icon = { Icon(dest.icon, null) }, label = { Text(dest.title) }) }
                    NavigationBarItem(selected = destinations.drop(3).any { it.screen == selected }, onClick = { more = true }, icon = { Icon(Icons.Default.MoreVert, null) }, label = { Text("More") })
                }
                if (more) AlertDialog(onDismissRequest = { more = false }, title = { Text("Open Quest Tuner") }, text = { Column { destinations.drop(3).forEach { dest ->
                    TextButton(onClick = { more = false; onNavigate(dest.screen) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(dest.icon, null); Spacer(Modifier.width(12.dp)); Text(dest.title, Modifier.weight(1f)) }
                } } }, confirmButton = { TextButton(onClick = { more = false }) { Text("Close") } })
            }
        }
    }
}

@Composable
fun ApertureMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = listOf(Color(0xFF82D5FF), Color(0xFF5E9BFF), Color(0xFF8A74E8), Color(0xFFEC92CF), Color(0xFFF3C590))
        colors.forEachIndexed { i, color -> drawArc(color, i * 72f - 92f, 60f, false, Offset(size.width * .11f, size.height * .11f), Size(size.width * .78f, size.height * .78f), style = Stroke(size.width * .24f, cap = StrokeCap.Butt)) }
    }
}

/** Small code-native glyphs keep the reference icon style crisp at every density. */
@Composable
fun VisionGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = Stroke(w * .08f, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(w*x,h*y),Offset(w*x2,h*y2),w*.08f,StrokeCap.Round)
        when (kind) {
            "device" -> { drawRoundRect(color,Offset(w*.1f,h*.25f),Size(w*.8f,h*.5f),androidx.compose.ui.geometry.CornerRadius(w*.15f),style=stroke); drawRoundRect(color,Offset(w*.32f,h*.38f),Size(w*.36f,h*.25f),androidx.compose.ui.geometry.CornerRadius(w*.1f),style=stroke) }
            "games" -> { drawRoundRect(color,Offset(w*.08f,h*.23f),Size(w*.84f,h*.57f),androidx.compose.ui.geometry.CornerRadius(w*.2f),style=stroke);line(.22f,.5f,.43f,.5f);line(.325f,.4f,.325f,.6f);drawCircle(color,w*.05f,Offset(w*.67f,h*.44f));drawCircle(color,w*.05f,Offset(w*.78f,h*.57f)) }
            "performance" -> { listOf(.38f,.65f,.88f).forEachIndexed { i, v -> drawRoundRect(color,Offset(w*(.14f+i*.28f),h*(1-v)),Size(w*.17f,h*(v-.08f)),androidx.compose.ui.geometry.CornerRadius(w*.03f)) } }
            "profiles" -> { for(i in 0..2) { val y=.22f+i*.28f; line(.1f,y,.9f,y); drawCircle(color,w*.1f,Offset(w*(if(i==1).67f else .32f),h*y)) } }
            "resolution" -> { drawRoundRect(color,Offset(w*.08f,h*.15f),Size(w*.84f,h*.6f),androidx.compose.ui.geometry.CornerRadius(w*.06f),style=stroke);line(.5f,.75f,.5f,.92f);line(.3f,.92f,.7f,.92f) }
            "cpu", "gpu" -> { drawRect(color,Offset(w*.23f,h*.23f),Size(w*.54f,h*.54f),style=stroke); for(i in 0..2) { val x=.32f+i*.18f;line(x,.06f,x,.2f);line(x,.8f,x,.94f);line(.06f,x,.2f,x);line(.8f,x,.94f,x) } }
            "auto" -> { line(.5f,.07f,.5f,.93f);line(.07f,.5f,.93f,.5f);line(.22f,.22f,.78f,.78f);line(.22f,.78f,.78f,.22f) }
            else -> { drawCircle(color,w*.37f,style=stroke);line(.5f,.24f,.5f,.5f);line(.5f,.5f,.72f,.61f) }
        }
    }
}

@Composable
fun OqtPanel(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = VisionPanel, border = BorderStroke(1.dp, VisionBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); action?.invoke() }
            content()
        }
    }
}
