package com.jprinz.luciddreamprovider.presentation

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState
import androidx.wear.tooling.preview.devices.WearDevices
import com.jprinz.luciddreamprovider.presentation.VibrationService
import com.jprinz.luciddreamprovider.presentation.theme.LucidDreamProviderTheme
import com.jprinz.luciddreamprovider.presentation.VibrationPattern
import com.jprinz.luciddreamprovider.presentation.availableVibrationPatterns

const val PREFS_NAME = "VibrationSettings"
const val KEY_VIBRATION_ENABLED = "vibration_enabled"
const val KEY_VIBRATION_INTERVAL_MINUTES = "vibration_interval_minutes"
const val KEY_VIBRATION_IN_SLEEP_MODE_ENABLED = "vibration_in_sleep_mode_enabled"
const val KEY_VIBRATION_PATTERN_ID = "vibration_pattern_id" 
const val DEFAULT_VIBRATION_INTERVAL_MINUTES = 20

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LucidDreamProviderTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var vibrationEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, false))
    }
    var intervalMinutesString by remember {
        mutableStateOf(
            sharedPreferences.getInt(
                KEY_VIBRATION_INTERVAL_MINUTES,
                DEFAULT_VIBRATION_INTERVAL_MINUTES
            ).toString()
        )
    }
    var vibrateInSleepModeEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_VIBRATION_IN_SLEEP_MODE_ENABLED, false))
    }
    
    var selectedPatternId by remember {
        mutableStateOf(sharedPreferences.getString(KEY_VIBRATION_PATTERN_ID, availableVibrationPatterns.first().id)!!)
    }
    val selectedPattern = remember(selectedPatternId) {
        availableVibrationPatterns.find { it.id == selectedPatternId } ?: availableVibrationPatterns.first()
    }

    var showPermissionScreen by remember { mutableStateOf(false) }

    if (showPermissionScreen) {
        PermissionRequestScreen(
            onGrantPermission = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Error starting settings intent: ", e)
                }
                showPermissionScreen = false
            },
            onCancel = {
                showPermissionScreen = false
            }
        )
    } else {
        val scrollState = rememberScalingLazyListState()
        Scaffold(
            timeText = { TimeText(modifier = Modifier.padding(top = 8.dp)) },
            positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                state = scrollState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                item { // Enable Vibration Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Vibration")
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { wantsToEnable ->
                                if (wantsToEnable) {
                                    val canSchedule = alarmManager.canScheduleExactAlarms()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canSchedule) {
                                        showPermissionScreen = true
                                    } else {
                                        vibrationEnabled = true
                                        sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, true).apply()
                                        startVibrationService(
                                            context,
                                            intervalMinutesString.toIntOrNull() ?: DEFAULT_VIBRATION_INTERVAL_MINUTES,
                                            vibrateInSleepModeEnabled,
                                            selectedPattern.id
                                        )
                                    }
                                } else {
                                    vibrationEnabled = false
                                    sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, false).apply()
                                    stopVibrationService(context)
                                }
                            }
                        )
                    }
                }

                item { // Vibrate in Sleep Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Vibrate in Sleep Mode",
                            color = if (vibrationEnabled) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = vibrateInSleepModeEnabled,
                            enabled = vibrationEnabled,
                            onCheckedChange = { isEnabled ->
                                vibrateInSleepModeEnabled = isEnabled
                                sharedPreferences.edit().putBoolean(KEY_VIBRATION_IN_SLEEP_MODE_ENABLED, isEnabled).apply()
                                if (vibrationEnabled) {
                                    startVibrationService(
                                        context,
                                        intervalMinutesString.toIntOrNull() ?: DEFAULT_VIBRATION_INTERVAL_MINUTES,
                                        isEnabled,
                                        selectedPattern.id
                                    )
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item { Text("Vibration Pattern") }
                item { // Pattern Picker
                    val patternPickerState = rememberPickerState(initialNumberOfOptions = availableVibrationPatterns.size, initiallySelectedOption = availableVibrationPatterns.indexOf(selectedPattern))
                    LaunchedEffect(patternPickerState.selectedOption) {
                        val newPattern = availableVibrationPatterns[patternPickerState.selectedOption]
                        if (newPattern.id != selectedPatternId) {
                            selectedPatternId = newPattern.id
                            sharedPreferences.edit().putString(KEY_VIBRATION_PATTERN_ID, newPattern.id).apply()
                            if (vibrationEnabled) { // Service mit neuem Muster aktualisieren UND testen
                                // Teste das neu ausgewählte Muster
                                testVibrationPattern(context, newPattern.id)

                                // Aktualisiere den Service für die nächste geplante Vibration
                                startVibrationService(
                                    context,
                                    intervalMinutesString.toIntOrNull() ?: DEFAULT_VIBRATION_INTERVAL_MINUTES,
                                    vibrateInSleepModeEnabled,
                                    newPattern.id
                                )
                            }
                        }
                    }

                    Picker(
                        state = patternPickerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp), // KORREKTUR: Feste Höhe hinzugefügt
                        readOnly = !vibrationEnabled
                    ) {
                        Text(availableVibrationPatterns[it].name, textAlign = TextAlign.Center)
                    }
                }
                // Preview Button wurde entfernt

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item { Text("Interval (Minutes)") }

                item { // Interval TextField
                    TextField(
                        value = intervalMinutesString,
                        onValueChange = { newValue ->
                            intervalMinutesString = newValue
                            val newInterval = newValue.toIntOrNull()
                            if (newInterval != null && newInterval > 0) {
                                sharedPreferences.edit().putInt(KEY_VIBRATION_INTERVAL_MINUTES, newInterval).apply()
                                if (vibrationEnabled) {
                                    startVibrationService(
                                        context,
                                        newInterval,
                                        vibrateInSleepModeEnabled,
                                        selectedPattern.id
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onGrantPermission: () -> Unit,
    onCancel: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText(modifier = Modifier.padding(top = 8.dp)) },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            state = scrollState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "To reliably schedule vibrations, this app needs permission for exact alarms. Please grant the permission in settings.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGrantPermission
                ) {
                    Text("To Settings")
                }
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.secondaryButtonColors(),
                    onClick = onCancel
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

fun startVibrationService(context: Context, intervalMinutes: Int, vibrateInSleepMode: Boolean, patternId: String) {
    val intent = Intent(context, VibrationService::class.java).apply {
        putExtra(VibrationService.EXTRA_INTERVAL_MINUTES, intervalMinutes)
        putExtra(VibrationService.EXTRA_VIBRATE_IN_SLEEP_MODE, vibrateInSleepMode)
        putExtra(VibrationService.EXTRA_VIBRATION_PATTERN_ID, patternId)
    }
    context.startService(intent)
}

fun testVibrationPattern(context: Context, patternId: String) {
    val intent = Intent(context, VibrationService::class.java).apply {
        action = VibrationService.ACTION_TEST_VIBRATION
        putExtra(VibrationService.EXTRA_VIBRATION_PATTERN_ID, patternId)
    }
    context.startService(intent)
}

fun stopVibrationService(context: Context) {
    val intent = Intent(context, VibrationService::class.java)
    context.stopService(intent)
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    LucidDreamProviderTheme {
        SettingsScreen()
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PermissionPreview() {
    LucidDreamProviderTheme {
        PermissionRequestScreen(onGrantPermission = {}, onCancel = {})
    }
}
