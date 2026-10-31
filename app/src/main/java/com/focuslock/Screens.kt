package com.focuslock

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private val DURS = listOf(
    "15m" to 15, "25m" to 25, "30m" to 30, "1h" to 60, "2h" to 120, "4h" to 240, "8h" to 480,
    "1 day" to 1440, "3 days" to 4320, "7 days" to 10080
)

fun a11yOn(c: Context): Boolean =
    (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        .contains(c.packageName)

fun openA11y(c: Context) =
    c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun openUsage(c: Context) =
    c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp), content = content
)

@Composable
fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(SURF).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Sub(label)
    }
}

@Composable
fun rememberTodayMs(now: Long): Long? {
    val c = LocalContext.current
    val granted = Usage.granted(c)
    val v by produceState<Long?>(null, granted, now / 60_000L) {
        value = if (granted) withContext(Dispatchers.IO) { Usage.todayTotal(c) } else null
    }
    return v
}

// ---------------------------------------------------------------- HOME
@Composable
fun HomeTab(now: Long) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(Store.apps(ctx)) }
    var adult by remember { mutableStateOf(Store.adult(ctx)) }
    var dur by remember { mutableIntStateOf(60) }
    var picker by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val end = Store.end(ctx)
    val manual = now < end
    val sched = Store.schedules(ctx).firstOrNull { it.on && it.activeNow() }
    val on = a11yOn(ctx)
    val today = rememberTodayMs(now)
    val goal = Store.goalMin(ctx) * 60_000L
    val score = today?.let { (100f * (1f - it.toFloat() / (goal * 2f))).coerceIn(0f, 100f).toInt() }
    val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = if (hr < 12) "Good morning" else if (hr < 18) "Good afternoon" else "Good evening"

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Sub(greet)
                Text("Focus Lock", color = TXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(SURF).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = WARN, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("${Store.streak(ctx)}", color = TXT, fontWeight = FontWeight.Bold)
            }
        }

        if (!on) Panel {
            H("Turn on the blocking engine")
            Sub("Enable Focus Lock in Accessibility settings. Nothing can be blocked without it.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Open settings") { openA11y(ctx) }
        }

        if (sched != null) Panel {
            H("${sched.name} is active")
            Sub("Locked until ${clock(sched.untilMillis())}", WARN)
        }

        // hero card
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GRAD).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (manual) {
                val left = end - now
                val prog = (left.toFloat() / Store.total(ctx)).coerceIn(0f, 1f)
                Text("Focus in progress", color = BG, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Ring(prog, 230.dp, 14.dp, Color.White.copy(alpha = 0.25f), SolidColor(BG)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt(left), color = BG, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("remaining", color = BG.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Locked until ${clock(end)}", color = BG, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    QUOTES[(dayOf(now) % QUOTES.size).toInt()],
                    color = BG.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center
                )
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Text("Ready to focus?", color = BG, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Block distractions for as long as you choose.", color = BG.copy(alpha = 0.8f), fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(durText(dur), color = BG, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURS.forEach { (l, m) -> HeroChip(l, dur == m) { dur = m } }
                    }
                    Spacer(Modifier.height(18.dp))
                    LightButton("Start focus", enabled = on && (apps.isNotEmpty() || adult)) { confirm = true }
                }
            }
        }

        // tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).height(160.dp).clickable(enabled = today == null) { openUsage(ctx) }) {
                Sub("Screen time today")
                Spacer(Modifier.height(6.dp))
                if (today != null) {
                    Text(fmtH(today), color = TXT, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SURF2)) {
                        Box(
                            Modifier.fillMaxWidth((today.toFloat() / goal).coerceIn(0.02f, 1f)).fillMaxHeight()
                                .clip(CircleShape).background(if (today > goal) RED else ACC)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Sub("Goal ${fmtH(goal)}")
                } else {
                    Text("--", color = TXT, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Sub("Tap to allow usage access", ACC)
                }
            }
            Panel(Modifier.weight(1f).height(160.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Ring((score ?: 0) / 100f, 92.dp, 10.dp) {
                        Text(score?.toString() ?: "--", color = TXT, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Sub("Focus score")
                }
            }
        }

        // block list
        Panel {
            H("Block list")
            if (manual) {
                Sub("These stay locked until the timer ends.")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Store.apps(ctx).take(10).forEach { AppIcon(it, 38.dp) }
                }
            } else {
                Sub("Choose what to block during focus.")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p ->
                        val sel = p.pkgs.all { it in apps }
                        Chip(p.name, sel) { apps = if (sel) apps - p.pkgs.toSet() else apps + p.pkgs }
                    }
                }
                val extra = apps - PRESETS.flatMap { it.pkgs }.toSet()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sub(if (extra.isEmpty()) "Add any other app" else "+${extra.size} more apps", TXT)
                    Spacer(Modifier.weight(1f))
                    TextButton({ picker = true }) { Text("Choose apps") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("18+ websites", color = TXT, modifier = Modifier.weight(1f))
                    Switch(adult, { adult = it })
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${Store.streak(ctx)}", "day streak", Modifier.weight(1f))
            StatTile("${Store.sessions(ctx)}", "sessions", Modifier.weight(1f))
            StatTile(fmtH(Store.focusMs(ctx)), "focused", Modifier.weight(1f))
        }
    }

    if (picker) AppPicker(apps, "Block these apps", onDone = { apps = it; picker = false }, onDismiss = { picker = false })
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Lock for ${durText(dur)}?") },
        text = { Text("Your selected apps stay blocked until ${clock(now + dur * 60_000L)}. There is no early unlock.") },
        confirmButton = {
            TextButton({ Store.startSession(ctx, dur * 60_000L, apps, adult); confirm = false }) { Text("Lock it") }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- SCHEDULES
@Composable
fun SchedulesTab(now: Long) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Store.schedules(ctx)) }
    var edit by remember { mutableStateOf<Schedule?>(null) }
    fun save(l: List<Schedule>) { list = l; Store.saveSchedules(ctx, l) }
    val letters = "MTWTFSS"

    Page {
        Text("Schedules", color = TXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Sub("Block apps automatically at set times. Active schedules can't be turned off or edited.")
        if (list.isEmpty()) Panel {
            H("No schedules yet")
            Sub("Try a quick template:")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Sleep 10pm to 7am", false) {
                    save(list + Schedule(System.currentTimeMillis(), "Sleep", 127, 22 * 60, 7 * 60, DEFAULT_APPS, true, true))
                }
                Chip("Study 9am to 1pm", false) {
                    save(list + Schedule(System.currentTimeMillis(), "Study", 31, 9 * 60, 13 * 60, DEFAULT_APPS, true, true))
                }
                Chip("Work 10am to 6pm", false) {
                    save(list + Schedule(System.currentTimeMillis(), "Work", 31, 10 * 60, 18 * 60, DEFAULT_APPS, true, true))
                }
            }
        }
        list.forEach { s ->
            val active = s.on && s.activeNow()
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        H(s.name)
                        Sub(letters.indices.filter { ((s.days shr it) and 1) == 1 }.joinToString(" ") { letters[it].toString() })
                        Sub("${timeStr(s.start)} to ${timeStr(s.end)}  ·  ${s.apps.size} apps" + if (s.adult) "  ·  18+" else "")
                    }
                    Switch(s.on, { v -> save(list.map { if (it.id == s.id) it.copy(on = v) else it }) }, enabled = !active)
                }
                if (active) {
                    Spacer(Modifier.height(6.dp))
                    Sub("Active now, locked until ${clock(s.untilMillis())}", WARN)
                }
                Row {
                    TextButton({ edit = s }, enabled = !active) { Text("Edit") }
                    TextButton({ save(list.filter { it.id != s.id }) }, enabled = !active) { Text("Delete", color = if (active) DIM else RED) }
                }
            }
        }
        PrimaryButton("New schedule") {
            edit = Schedule(System.currentTimeMillis(), "", 31, 22 * 60, 7 * 60, DEFAULT_APPS, true, true)
        }
    }
    edit?.let { e ->
        ScheduleEditor(
            e, onDismiss = { edit = null },
            onSave = { s ->
                save(if (list.any { it.id == s.id }) list.map { if (it.id == s.id) s else it } else list + s)
                edit = null
            }
        )
    }
}

@Composable
fun ScheduleEditor(init: Schedule, onSave: (Schedule) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(init) }
    var picker by remember { mutableStateOf(false) }
    val letters = "MTWTFSS"
    fun pickTime(cur: Int, set: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, cur / 60, cur % 60, false).show()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(SURF).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            H(if (init.name.isEmpty()) "New schedule" else "Edit schedule")
            OutlinedTextField(
                value = s.name, onValueChange = { s = s.copy(name = it) }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                letters.forEachIndexed { i, ch ->
                    val on = ((s.days shr i) and 1) == 1
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (on) ACC else SURF2)
                            .clickable { s = s.copy(days = s.days xor (1 shl i)) },
                        contentAlignment = Alignment.Center
                    ) { Text(ch.toString(), color = if (on) Color.White else DIM, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("From ${timeStr(s.start)}", false) { pickTime(s.start) { s = s.copy(start = it) } }
                Chip("To ${timeStr(s.end)}", false) { pickTime(s.end) { s = s.copy(end = it) } }
            }
            Chip("${s.apps.size} apps selected", true) { picker = true }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("18+ websites", color = TXT, modifier = Modifier.weight(1f))
                Switch(s.adult, { s = s.copy(adult = it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton(
                    { onSave(s) },
                    enabled = s.name.isNotBlank() && s.days != 0 && s.start != s.end
                ) { Text("Save") }
            }
        }
    }
    if (picker) AppPicker(s.apps, "Schedule apps", onDone = { s = s.copy(apps = it); picker = false }, onDismiss = { picker = false })
}

// ---------------------------------------------------------------- INSIGHTS
@Composable
fun BarChart(values: List<Long>, baseline: Long, goal: Long) {
    val mx = maxOf(values.maxOrNull() ?: 0L, baseline, goal, 1L).toFloat()
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val n = values.size
        val gap = size.width / n
        val bw = gap * 0.55f
        values.forEachIndexed { i, v ->
            val h = (size.height * v / mx).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (i == n - 1) ACC else ACC.copy(alpha = 0.35f),
                topLeft = Offset(i * gap + (gap - bw) / 2, size.height - h),
                size = Size(bw, h), cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        if (baseline > 0) {
            val y = size.height - size.height * baseline / mx
            drawLine(WARN, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
    }
}

@Composable
fun InsightsTab(now: Long) {
    val ctx = LocalContext.current
    val granted = Usage.granted(ctx)
    var data by remember { mutableStateOf<InsightData?>(null) }
    var range by remember { mutableIntStateOf(7) }
    LaunchedEffect(granted) {
        if (granted) data = withContext(Dispatchers.IO) { Usage.load(ctx) }
    }
    Page {
        Text("Insights", color = TXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (!granted) Panel {
            H("Allow usage access")
            Sub("Focus Lock needs this to show your screen time, enforce daily limits and measure your improvement. Data never leaves your phone.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Grant access") { openUsage(ctx) }
        } else {
            val d = data
            if (d == null) Sub("Loading...") else {
                val goal = Store.goalMin(ctx) * 60_000L
                Panel {
                    Sub("Screen time today")
                    Text(fmtH(d.today), color = TXT, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Sub("Daily goal ${fmtH(goal)}", if (d.today > goal) RED else MINT)
                }

                Panel {
                    H("Your progress")
                    Spacer(Modifier.height(6.dp))
                    if (d.baseline > 0 && d.sinceDays >= 1) {
                        val pct = ((d.baseline - d.sinceAvg) * 100f / d.baseline).toInt()
                        Text(
                            (if (pct >= 0) "-" else "+") + "${kotlin.math.abs(pct)}%",
                            color = if (pct >= 0) MINT else RED, fontSize = 38.sp, fontWeight = FontWeight.Bold
                        )
                        Sub("daily screen time vs before Focus Lock")
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) { Sub("Before"); Text(fmtH(d.baseline) + "/day", color = TXT, fontWeight = FontWeight.SemiBold) }
                            Column(Modifier.weight(1f)) { Sub("Now (avg)"); Text(fmtH(d.sinceAvg) + "/day", color = TXT, fontWeight = FontWeight.SemiBold) }
                            Column(Modifier.weight(1f)) { Sub("Time saved"); Text(fmtH(d.saved), color = MINT, fontWeight = FontWeight.SemiBold) }
                        }
                    } else {
                        Sub("We saved your starting point from the past 7 days. Check back tomorrow to see your improvement.")
                    }
                }

                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        H("Screen time")
                        Spacer(Modifier.weight(1f))
                        Chip("7 days", range == 7) { range = 7 }
                        Spacer(Modifier.width(8.dp))
                        Chip("30 days", range == 30) { range = 30 }
                    }
                    Spacer(Modifier.height(14.dp))
                    BarChart(d.series.takeLast(range), d.baseline, goal)
                    Spacer(Modifier.height(6.dp))
                    if (range == 7) {
                        val days = (6 downTo 0).map { off ->
                            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -off)
                            "SMTWTFS"[cal.get(Calendar.DAY_OF_WEEK) - 1].toString()
                        }
                        Row(Modifier.fillMaxWidth()) {
                            days.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Sub(it) } }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) { Sub("30 days ago"); Spacer(Modifier.weight(1f)); Sub("Today") }
                    }
                    if (d.baseline > 0) {
                        Spacer(Modifier.height(8.dp))
                        Sub("Dashed line is your average before Focus Lock.", WARN)
                    }
                    Spacer(Modifier.height(8.dp))
                    Sub("Goal met on ${d.goalDays} of the last 7 days.")
                }

                Panel {
                    H("Most used today")
                    Spacer(Modifier.height(8.dp))
                    if (d.top.isEmpty()) Sub("No usage recorded yet.")
                    val topMax = maxOf(d.top.firstOrNull()?.ms ?: 1L, 1L)
                    d.top.forEach { u ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(u.pkg, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.name, color = TXT, fontSize = 14.sp)
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(SURF2)) {
                                    Box(Modifier.fillMaxWidth(u.ms.toFloat() / topMax).fillMaxHeight().clip(CircleShape).background(ACC))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Sub(fmtH(u.ms))
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${Store.streak(ctx)}", "day streak", Modifier.weight(1f))
            StatTile("${Store.sessions(ctx)}", "sessions", Modifier.weight(1f))
            StatTile(fmtH(Store.focusMs(ctx)), "focused", Modifier.weight(1f))
        }

        Panel {
            H("Achievements")
            Spacer(Modifier.height(10.dp))
            val saved = data?.saved ?: 0L
            val badges = listOf(
                Triple("First focus", "Finish 1 session", Store.sessions(ctx) >= 1),
                Triple("Hat trick", "3 day streak", Store.best(ctx) >= 3),
                Triple("On fire", "7 day streak", Store.best(ctx) >= 7),
                Triple("Committed", "10 sessions", Store.sessions(ctx) >= 10),
                Triple("Deep work", "10 hours focused", Store.focusMs(ctx) >= 36_000_000L),
                Triple("Time saver", "Save 5 hours", saved >= 18_000_000L)
            )
            badges.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (t, desc, got) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (got) ACC.copy(alpha = 0.10f) else SURF2).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = if (got) ACC else DIM.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(t, color = if (got) TXT else DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(desc, color = DIM, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- TOOLS
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsTab(now: Long) {
    val ctx = LocalContext.current
    val locked = Blocking.current(ctx) != null
    var goal by remember { mutableIntStateOf(Store.goalMin(ctx)) }
    var pause by remember { mutableStateOf(Store.pauseApps(ctx)) }
    var secs by remember { mutableIntStateOf(Store.pauseSecs(ctx)) }
    var limits by remember { mutableStateOf(Store.limits(ctx)) }
    var kw by remember { mutableStateOf(Store.keywords(ctx)) }
    var newKw by remember { mutableStateOf("") }
    var pick by remember { mutableIntStateOf(0) }
    val a11 = a11yOn(ctx)
    val usage = Usage.granted(ctx)

    Page {
        Text("Tools", color = TXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        Panel {
            H("Setup")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Blocking engine", color = TXT, modifier = Modifier.weight(1f))
                if (a11) Sub("On", MINT) else TextButton({ openA11y(ctx) }) { Text("Enable") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usage access", color = TXT, modifier = Modifier.weight(1f))
                if (usage) Sub("On", MINT) else TextButton({ openUsage(ctx) }) { Text("Enable") }
            }
        }

        Panel {
            H("Daily screen time goal")
            Sub("Used for your focus score and goal tracking.")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip("-", false) { if (goal > 30) { goal -= 30; Store.setGoalMin(ctx, goal) } }
                Text(fmtH(goal * 60_000L), color = TXT, fontWeight = FontWeight.Bold)
                Chip("+", false) { if (goal < 720) { goal += 30; Store.setGoalMin(ctx, goal) } }
            }
        }

        Panel {
            H("Mindful pause")
            Sub("A short breathing pause before these apps open. You can still continue after it.")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("${pause.size} apps", true) { pick = 1 }
                Chip("-", false) { if (secs > 5) { secs -= 5; Store.setPauseSecs(ctx, secs) } }
                Text("${secs}s", color = TXT)
                Chip("+", false) { if (secs < 30) { secs += 5; Store.setPauseSecs(ctx, secs) } }
            }
        }

        Panel {
            H("Daily limits")
            Sub("Block an app for the rest of the day once you pass the limit. Needs usage access.")
            Spacer(Modifier.height(8.dp))
            limits.forEach { (pkg, min) ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(Usage.label(ctx, pkg), color = TXT, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    TextButton({
                        if (min > 10) { limits = limits + (pkg to min - 10); Store.saveLimits(ctx, limits) }
                    }) { Text("-") }
                    Text("${min}m", color = TXT)
                    TextButton({
                        if (min < 600) { limits = limits + (pkg to min + 10); Store.saveLimits(ctx, limits) }
                    }) { Text("+") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Chip("Add or remove apps", false) { pick = 2 }
        }

        Panel {
            H("Blocked words")
            Sub("Extra words that trigger the 18+ filter in browser address bars.")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kw.forEach { w ->
                    Chip("$w  ×", false) {
                        if (!locked) { kw = kw - w; Store.saveKeywords(ctx, kw) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newKw, onValueChange = { newKw = it }, singleLine = true,
                    placeholder = { Text("Add a word or site") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                TextButton({
                    val w = newKw.trim().lowercase()
                    if (w.isNotEmpty()) { kw = kw + w; Store.saveKeywords(ctx, kw); newKw = "" }
                }) { Text("Add") }
            }
        }
    }

    if (pick == 1) AppPicker(pause, "Mindful pause apps", false,
        onDone = { pause = it; Store.savePauseApps(ctx, it); pick = 0 }, onDismiss = { pick = 0 })
    if (pick == 2) AppPicker(limits.keys, "Daily limits", false,
        onDone = { sel ->
            limits = sel.associateWith { limits[it] ?: 30 }
            Store.saveLimits(ctx, limits); pick = 0
        }, onDismiss = { pick = 0 })
}
