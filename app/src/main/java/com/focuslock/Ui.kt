package com.focuslock

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val BG = Color(0xFFF8F3F0)
val SURF = Color(0xFFFFFFFF)
val SURF2 = Color(0xFFEFE6E0)
val LINE = Color(0xFFE9DFD8)
val ACC = Color(0xFF014BAA)
val ACC2 = Color(0xFF2F7FE0)
val MINT = Color(0xFF1E9E5A)
val WARN = Color(0xFFD9822B)
val RED = Color(0xFFD64545)
val TXT = Color(0xFF0F1B33)
val DIM = Color(0xFF6B7686)
val GRAD = Brush.linearGradient(listOf(ACC, ACC2))

fun fmt(ms: Long): String {
    val s = maxOf(ms, 0L) / 1000
    val d = s / 86400
    return (if (d > 0) "${d}d " else "") + "%02d:%02d:%02d".format((s % 86400) / 3600, (s % 3600) / 60, s % 60)
}

fun fmtH(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun durText(m: Int): String = when {
    m < 60 -> "$m min"
    m < 1440 -> "${m / 60} hr"
    else -> "${m / 1440} day" + if (m / 1440 > 1) "s" else ""
}

fun timeStr(min: Int) = "%02d:%02d".format(min / 60, min % 60)
fun clock(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))

fun ComponentActivity.styleBars(status: Color, nav: Color, darkIcons: Boolean) {
    window.statusBarColor = status.toArgb()
    window.navigationBarColor = nav.toArgb()
    val ctl = WindowCompat.getInsetsController(window, window.decorView)
    ctl.isAppearanceLightStatusBars = darkIcons
    ctl.isAppearanceLightNavigationBars = darkIcons
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ACC, onPrimary = Color.White, background = BG, surface = SURF, onSurface = TXT,
            onBackground = TXT, surfaceVariant = SURF2, onSurfaceVariant = DIM
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = BG, contentColor = TXT, content = content)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SURF)
            .border(1.dp, LINE, RoundedCornerShape(24.dp)).padding(18.dp),
        content = content
    )

@Composable
fun H(text: String) = Text(text, color = TXT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Sub(text: String, color: Color = DIM) = Text(text, color = color, fontSize = 13.sp)

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) ACC.copy(alpha = 0.12f) else SURF2)
            .border(1.dp, if (on) ACC else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(text, color = if (on) ACC else DIM, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun HeroChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) BG else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(text, color = if (on) ACC else BG, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) GRAD else SolidColor(SURF2))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) Color.White else DIM, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun LightButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) BG else BG.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = ACC, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Ring(
    progress: Float, dia: Dp, stroke: Dp = 14.dp, track: Color = SURF2, arc: Brush = GRAD,
    content: @Composable BoxScope.() -> Unit
) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val s = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
            drawArc(
                brush = arc, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = s, style = Stroke(w, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun AppIcon(pkg: String, dia: Dp = 40.dp) {
    val c = LocalContext.current
    val bmp = remember(pkg) {
        try {
            c.packageManager.getApplicationIcon(if (pkg == SHORTS) YT else pkg).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) { null }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(dia).clip(RoundedCornerShape(12.dp)))
    else Box(Modifier.size(dia).clip(CircleShape).background(SURF2))
}

@Composable
fun AppPicker(
    initial: Set<String>, title: String, allowShorts: Boolean = true,
    onDone: (Set<String>) -> Unit, onDismiss: () -> Unit
) {
    val c = LocalContext.current
    val all = remember {
        val pm = c.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != c.packageName }
            .sortedBy { it.second.lowercase() }
        if (allowShorts) listOf(SHORTS to "YouTube Shorts (only Shorts)") + list else list
    }
    var sel by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BG).padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Sub("${sel.size} selected")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(all.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) }, key = { it.first }) { (pkg, name) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { sel = if (pkg in sel) sel - pkg else sel + pkg }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(pkg)
                        Spacer(Modifier.width(14.dp))
                        Text(name, color = TXT, modifier = Modifier.weight(1f))
                        Switch(checked = pkg in sel, onCheckedChange = null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Done") { onDone(sel) }
        }
    }
}
