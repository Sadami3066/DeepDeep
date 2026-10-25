package io.github.whywood.deepdeep.plugin.arrange

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.FontPreferences
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.deepDeepColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ArrangeAlarmActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val alarmId = intent.getStringExtra(ArrangeAlarmReceiver.EXTRA_ALARM_ID).orEmpty()
        val stored = alarmId.takeIf { it.isNotBlank() }?.let { id ->
            runCatching { arrangeAlarmStore(this).find(id) }.getOrNull()
        }
        val label = intent.getStringExtra(ArrangeAlarmReceiver.EXTRA_ALARM_LABEL)
            ?: stored?.label.orEmpty()
        val firedAt = if (intent.hasExtra(ArrangeAlarmReceiver.EXTRA_ALARM_FIRED_AT)) {
            intent.getLongExtra(ArrangeAlarmReceiver.EXTRA_ALARM_FIRED_AT, System.currentTimeMillis())
        } else {
            stored?.triggerAt ?: System.currentTimeMillis()
        }
        setContent {
            val fontStyle = remember { FontPreferences(applicationContext).read() }
            DeepDeepTheme(fontStyle = fontStyle) {
                BackHandler { stopAlarm() }
                AlarmContent(label = label, firedAt = firedAt, onStop = ::stopAlarm)
            }
        }
    }

    private fun stopAlarm() {
        runCatching {
            startService(
                Intent(this, ArrangeAlarmService::class.java)
                    .setAction(ArrangeAlarmService.ACTION_STOP),
            )
        }
        finish()
    }
}

@Composable
private fun AlarmContent(label: String, firedAt: Long, onStop: () -> Unit) {
    val colors = deepDeepColors()
    val time = remember(firedAt) {
        Instant.ofEpochMilli(firedAt).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = time,
            style = DdType.display,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        if (label.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = label,
                style = DdType.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(56.dp))
        DdPrimaryButton(
            text = stringResource(R.string.notification_action_stop),
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
