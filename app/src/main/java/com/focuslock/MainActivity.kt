package com.focuslock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BG = Color(0xFF0B0F1A)
private val CARD = Color(0xFF151B2B)
private val ACC = Color(0xFF7C9CFF)
private val MINT = Color(0xFF5EEAD4)
private val DIM = Color(0xFF8A93A8)
private const val WAIT = 5 * 60 * 1000L

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ACC, background = BG, surface = CARD)) {
                Box(Modifier.fillMaxSize().background(BG).systemBarsPadding()) { App() }
            }
        }
    }
}

fun fmt(ms: Long): String {
    val s = maxOf(ms, 0) / 1000
    val d = s / 86400
    return (if (d > 0) "${d}d " else "") + "%02d:%02d:%02d".format((s % 86400) / 3600, (s % 3600) / 60, s % 60)
}

fun a11yOn(c: Context): Boolean =
    (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        .contains(c.packageName)

@Composable
fun App() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val end = Store.end(ctx)
    if (end != 0L && now >= end) Store.clear(ctx)
    if (end != 0L && now < end) LockedScreen(ctx, now, end) else SetupScreen(ctx) { now = System.currentTimeMillis() }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(20.dp)).padding(16.dp), content = content
)

@Composable
fun Stepper(label: String, v: Int, max: Int, mod: Modifier, set: (Int) -> Unit) =
    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = DIM, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ if (v > 0) set(v - 1) }) { Text("-", fontSize = 22.sp) }
            Text("$v", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton({ if (v < max) set(v + 1) }) { Text("+", fontSize = 22.sp) }
        }
    }

@Composable
fun SetupScreen(ctx: Context, onStart: () -> Unit) {
    var d by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(1) }
    var m by remember { mutableIntStateOf(0) }
    var adult by remember { mutableStateOf(Store.adult(ctx)) }
    val sel = remember { mutableStateListOf<String>().apply { addAll(Store.apps(ctx)) } }
    var confirm by remember { mutableStateOf(false) }
    val on = a11yOn(ctx)
    val total = ((d * 24L + h) * 60 + m) * 60_000L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("FocusLock", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Take your attention back.", color = DIM)
        if (!on) Card {
            Text("Step 1: Enable blocking", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Turn on FocusLock in Accessibility settings. Without it nothing can be blocked.", color = DIM, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button({ ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open settings") }
        } else Text("Blocking engine: ON", color = MINT)

        Card {
            Text("How long?", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
                Stepper("DAYS", d, 30, Modifier.weight(1f)) { d = it }
                Stepper("HOURS", h, 23, Modifier.weight(1f)) { h = it }
                Stepper("MINUTES", m, 59, Modifier.weight(1f)) { m = it }
            }
        }
        Card {
            Text("Block these", color = Color.White, fontWeight = FontWeight.SemiBold)
            ITEMS.forEach { it ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(it.name, color = Color.White, modifier = Modifier.weight(1f))
                    Switch(it.name in sel, { on -> if (on) sel.add(it.name) else sel.remove(it.name) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("18+ websites", color = Color.White, modifier = Modifier.weight(1f))
                Switch(adult, { adult = it })
            }
        }
        Button(
            onClick = { confirm = true }, enabled = on && total > 0 && (sel.isNotEmpty() || adult),
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
        ) { Text("Start focus session", fontSize = 17.sp) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Lock for ${fmt(total)}?") },
        text = { Text("Selected apps will be blocked until the timer ends. Emergency unlock needs a 5 minute wait.") },
        confirmButton = { TextButton({ Store.start(ctx, total, sel.toSet(), adult); confirm = false; onStart() }) { Text("Lock it") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
    )
}

@Composable
fun LockedScreen(ctx: Context, now: Long, end: Long) {
    val left = end - now
    val prog = (left.toFloat() / Store.total(ctx)).coerceIn(0f, 1f)
    val ov = Store.override(ctx)
    val wait = if (ov == 0L) -1L else WAIT - (now - ov)
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Stay focused", color = DIM, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 18.dp.toPx()
                val s = Size(size.width - w, size.height - w)
                drawArc(Color(0xFF1E2640), 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
                drawArc(ACC, -90f, 360f * prog, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(fmt(left), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Text("Your apps are locked until the timer ends.", color = DIM)
        Spacer(Modifier.height(32.dp))
        when {
            ov == 0L -> OutlinedButton({ Store.setOverride(ctx, System.currentTimeMillis()) }) { Text("Emergency unlock") }
            wait > 0 -> Card {
                Text("Unlock available in ${fmt(wait)}", color = Color.White)
                TextButton({ Store.setOverride(ctx, 0L) }) { Text("Cancel, keep focusing") }
            }
            else -> Button({ Store.clear(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))) { Text("Unlock now") }
        }
    }
}
