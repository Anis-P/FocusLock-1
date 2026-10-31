package com.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val QUOTES = listOf(
    "Your attention is your most valuable asset.",
    "Boredom is where ideas are born.",
    "Close the app. Open your life.",
    "Small focused hours beat long distracted days.",
    "You will not regret the time you took back.",
    "Discipline is choosing what you want most over what you want now."
)

private fun ComponentActivity.goHome() {
    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    finish()
}

class BlockActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        styleBars(BG, BG, true)
        val name = intent.getStringExtra("name") ?: ""
        val reason = intent.getStringExtra("reason") ?: ""
        setContent {
            AppTheme {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(GRAD), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        if (name.isEmpty()) "Blocked" else "$name is blocked",
                        color = TXT, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Sub(reason)
                    Spacer(Modifier.height(28.dp))
                    Text(QUOTES.random(), color = DIM, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(36.dp))
                    PrimaryButton("Back to home") { goHome() }
                }
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() = goHome()

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}

class PauseActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        styleBars(BG, BG, true)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val secs = Store.pauseSecs(this)
        setContent {
            AppTheme {
                var left by remember { mutableIntStateOf(secs) }
                LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
                val t = rememberInfiniteTransition(label = "breath")
                val s by t.animateFloat(
                    0.6f, 1f,
                    infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "scale"
                )
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().scale(s).clip(CircleShape).background(GRAD))
                        Text(if (left > 0) "$left" else "", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(32.dp))
                    Text("Take a breath", color = TXT, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Sub("Do you really want to open ${Usage.label(this@PauseActivity, pkg)}?")
                    Spacer(Modifier.height(36.dp))
                    PrimaryButton("Open anyway", enabled = left <= 0) {
                        Store.setGrace(this@PauseActivity, pkg, System.currentTimeMillis() + 5 * 60_000L)
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                        finish()
                    }
                    TextButton({ goHome() }) { Text("Not now") }
                }
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() = goHome()

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}
