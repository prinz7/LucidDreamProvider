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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Alert // Sicherstellen, dass Alert importiert ist
import androidx.wear.tooling.preview.devices.WearDevices
import com.jprinz.luciddreamprovider.presentation.theme.LucidDreamProviderTheme

const val PREFS_NAME = "VibrationSettings"
const val KEY_VIBRATION_ENABLED = "vibration_enabled"
const val KEY_VIBRATION_INTERVAL_MINUTES = "vibration_interval_minutes"
const val DEFAULT_VIBRATION_INTERVAL_MINUTES = 1

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
    var showPermissionDialog by remember { mutableStateOf(false) } 

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
                        Log.d("SettingsScreen", "Wants to enable. Build SDK: ${Build.VERSION.SDK_INT}, Can schedule exact alarms: $canSchedule")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canSchedule) {
                            Log.d("SettingsScreen", "Setting showPermissionDialog to true")
                            showPermissionDialog = true
                        } else {
                            Log.d("SettingsScreen", "Skipping permission dialog. Enabling vibration or permission already granted.")
                            vibrationEnabled = true
                            sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, true).apply()
                            startVibrationService(context, intervalMinutesString.toIntOrNull() ?: DEFAULT_VIBRATION_INTERVAL_MINUTES)
                        }
                    } else {
                        Log.d("SettingsScreen", "Wants to disable vibration.")
                        vibrationEnabled = false
                        sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, false).apply()
                        stopVibrationService(context)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Interval (Minutes)")

        TextField(
            value = intervalMinutesString,
            onValueChange = { newValue ->
                intervalMinutesString = newValue
                val newInterval = newValue.toIntOrNull()
                if (newInterval != null && newInterval > 0) {
                    sharedPreferences.edit().putInt(KEY_VIBRATION_INTERVAL_MINUTES, newInterval).apply()
                    if (vibrationEnabled) {
                        startVibrationService(context, newInterval)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showPermissionDialog) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxContentHeight = screenHeight * 0.5f

        Alert(
            title = { Text("Permission Required") },
            content = {
                // Determine a reasonable max height based on the screen height
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val maxContentHeight = screenHeight * 0.5f

                val scrollState = rememberScrollState()

                // Keep the original Column logic but make it scrollable and bounded in height
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight)   // limit content height so buttons remain visible
                        .verticalScroll(scrollState)        // allow scrolling for long content
                        .padding(horizontal = 8.dp)         // optional padding for better layout
                ) {
                    Text(
                        text = "To reliably schedule vibrations in the background, this app needs permission for exact alarms. Please grant the permission in settings."
                        // No explicit style or textAlign here, uses defaults as before
                    )
                }
            },
            negativeButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(), // same as before
                    colors = ButtonDefaults.secondaryButtonColors(),
                    onClick = { showPermissionDialog = false }
                ) { Text("Cancel") }
            },
            positiveButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(), // same as before
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            showPermissionDialog = false
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Error starting settings intent: ", e)
                            showPermissionDialog = false
                        }
                    }
                ) { Text("To Settings") }
            }
        )
    }
}

fun startVibrationService(context: Context, intervalMinutes: Int) {
    val intent = Intent(context, VibrationService::class.java).apply {
        putExtra(VibrationService.EXTRA_INTERVAL_MINUTES, intervalMinutes)
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
