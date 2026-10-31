package com.focuslock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        styleBars(ACC, ACC, false)
        setContent { AppTheme { Root() } }
    }
}

@Composable
fun Root() {
    val act = LocalContext.current as ComponentActivity
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (splash) {
            act.styleBars(ACC, ACC, false)
            delay(2200)
            splash = false
        }
        act.styleBars(BG, SURF, true)
    }
    Crossfade(targetState = splash, animationSpec = tween(500), label = "root") { s ->
        if (s) Splash() else Shell()
    }
}

@Composable
fun Splash() {
    val sc = remember { Animatable(0.5f) }
    val al = remember { Animatable(0f) }
    val sw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { sc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        launch { al.animateTo(1f, tween(900)) }
        launch { sw.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    }
    Box(Modifier.fillMaxSize().background(GRAD), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).scale(sc.value), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 8.dp.toPx()
                    val s = Size(size.width - w, size.height - w)
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
                    drawArc(BG, -90f, 360f * sw.value, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
                }
                Icon(Icons.Rounded.Lock, null, tint = BG, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Focus Lock", color = BG, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(al.value))
            Spacer(Modifier.height(6.dp))
            Text("Take your time back", color = BG.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.alpha(al.value))
        }
    }
}

@Composable
fun Onboarding(now: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val a11 = a11yOn(ctx)
    val usage = Usage.granted(ctx)
    val title = when (step) {
        0 -> "Take your time back"
        1 -> "Turn on blocking"
        else -> "See your progress"
    }
    val body = when (step) {
        0 -> "Block reels, shorts and other distractions on a timer you can't cheat, and see how much screen time you save."
        1 -> "Allow Focus Lock in Accessibility settings. This lets it see which app is open so it can block it. Nothing leaves your phone."
        else -> "Allow usage access to track your screen time, set daily limits and measure your improvement."
    }
    Column(
        Modifier.fillMaxSize().background(BG).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(GRAD), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = TXT, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, color = DIM, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        if (step == 1 || step == 2) {
            val ok = if (step == 1) a11 else usage
            if (ok) Text("Enabled", color = MINT, fontWeight = FontWeight.Bold)
            else Chip("Open settings", true) { if (step == 1) openA11y(ctx) else openUsage(ctx) }
            if (step == 1 && !a11) {
                Spacer(Modifier.height(10.dp))
                Sub("If it says Restricted setting: App info, three dots, Allow restricted settings.")
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { i ->
                Box(Modifier.size(if (i == step) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == step) ACC else SURF2))
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            if (step == 0) "Get started" else if (step == 1) "Next" else "Finish",
            enabled = step != 1 || a11
        ) { if (step < 2) step++ else onDone() }
        if (step == 2 && !usage) TextButton({ onDone() }) { Text("Skip for now") }
    }
}

@Composable
fun Shell() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var onb by remember { mutableStateOf(Store.onboarded(ctx)) }

    val end = Store.end(ctx)
    if (end != 0L && now >= end) Store.complete(ctx)

    if (!onb) {
        Onboarding(now) { Store.setOnboarded(ctx); onb = true }
        return
    }

    val items = listOf(
        "Home" to Icons.Rounded.Home, "Schedules" to Icons.Rounded.Schedule,
        "Insights" to Icons.Rounded.BarChart, "Tools" to Icons.Rounded.Tune
    )
    Column(Modifier.fillMaxSize().background(BG)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeTab(now)
                1 -> SchedulesTab(now)
                2 -> InsightsTab(now)
                else -> ToolsTab(now)
            }
        }
        NavigationBar(containerColor = SURF) {
            items.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, null) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ACC, selectedTextColor = ACC,
                        indicatorColor = ACC.copy(alpha = 0.12f),
                        unselectedIconColor = DIM, unselectedTextColor = DIM
                    )
                )
            }
        }
    }
}
