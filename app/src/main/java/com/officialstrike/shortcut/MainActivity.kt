package com.officialstrike.shortcut

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.officialstrike.shortcut.ui.theme.ShortcutTheme
import java.util.*
import kotlin.concurrent.schedule


enum class ConfigSoundModes {
    SILENT, VIBRATE
}
// TODO: https://developer.android.com/studio/write/image-asset-studio
private var soundMode by mutableStateOf(ConfigSoundModes.SILENT);
private var buttonClicks by mutableStateOf(2)
private var clickTime by mutableStateOf(250)


private var isAppRunningInBackground by mutableStateOf(false)

class MainActivity : ComponentActivity() {

    private lateinit var notificationManager: NotificationManager
    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        soundMode = ConfigSoundModes.valueOf(
            sharedPref.getString("sound_mode", null) ?: with(sharedPref.edit()) {
                putString("sound_mode", ConfigSoundModes.SILENT.name)
                apply()
                ConfigSoundModes.SILENT.name
            })

        buttonClicks = sharedPref.getInt("button_clicks", -1)
        if (buttonClicks == -1) {
            with(sharedPref.edit()) {
                putInt("button_clicks", 2)
                apply()
            }
            buttonClicks = 2
        }

        clickTime = sharedPref.getInt("click_time", -1)
        if (clickTime == -1) {
            with(sharedPref.edit()) {
                putInt("click_time", 250)
                apply()
            }
            clickTime = 250
        }

        notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var intent = Intent(this, ForegroundService::class.java);
        if (notificationManager.isNotificationPolicyAccessGranted) {

            permissionsGranted = true
        }

        setContent {

            ShortcutTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    if (permissionsGranted) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(text = if (isAppRunningInBackground) "Service is currently running" else "Service is not currently running")
                            Button(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .padding(8.dp),
                                enabled = !isAppRunningInBackground,

                                onClick = {
                                    if (!isAppRunningInBackground) {
                                        startService(intent)
                                        isAppRunningInBackground = true
                                    }
                                }
                            ) {
                                Text("Run service")
                            }

                            Button(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .padding(8.dp),
                                enabled = isAppRunningInBackground,
                                onClick = {
                                    if (isAppRunningInBackground) {
                                        stopService(intent)
                                        isAppRunningInBackground = false
                                    }
                                }
                            ) {
                                Text("Stop service")
                            }

                            Button(onClick = {
                                soundMode = when (soundMode) {
                                    ConfigSoundModes.VIBRATE -> ConfigSoundModes.SILENT
                                    ConfigSoundModes.SILENT -> ConfigSoundModes.VIBRATE
                                }
                                with(sharedPref.edit()) {
                                    putString("sound_mode", soundMode.name)
                                    apply()
                                }
                            }) {
                                Text(text = "$soundMode mode")
                            }


                            Text(text = "$buttonClicks clicks")
                            Slider(
                                value = buttonClicks.toFloat(),
                                onValueChange = { buttonClicks = it.toInt(); },
                                valueRange = 2f..50f, // you are legitimately insane if you require 50 clicks
                                modifier = Modifier.size(190.dp, 40.dp),


                                onValueChangeFinished = {
                                    with(sharedPref.edit()) {
                                        putInt("button_clicks", buttonClicks)
                                        apply()
                                    }
                                },
                            )


                            Text(text = "click time: $clickTime milliseconds")
                            Slider(
                                value = clickTime.toFloat(),
                                onValueChange = { clickTime = it.toInt(); },
                                valueRange = 100f..1000f,
                                modifier = Modifier.size(190.dp, 40.dp),


                                onValueChangeFinished = {
                                    with(sharedPref.edit()) {
                                        putInt("click_time", clickTime)
                                        apply()
                                    }
                                },
                            )
                        }
                    } else getPermissions()
                }
            }

        }

    }

    @Composable
    fun getPermissions() {
        return androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = "Permission needed")
            },
            containerColor = MaterialTheme.colors.background,
            text = {
                Text(text = "This app needs permission to modify the notification policy. Please grant access in the settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        startActivity(intent)
                    }
                ) {
                    Text("Go To settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            permissionsGranted = true
                        }
                    }
                ) {
                    Text("Dismiss")
                }
            }

        )


    }

}

class ForegroundService : Service() {
    private var isPowerButtonPressed = 1
    private var powerButtonPressTime: Long = 0
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun toggleSilentMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (soundMode) {
            ConfigSoundModes.SILENT -> setRingerMode(audioManager, AudioManager.RINGER_MODE_SILENT)
            ConfigSoundModes.VIBRATE -> setRingerMode(
                audioManager,
                AudioManager.RINGER_MODE_VIBRATE
            )
        }

    }
    private fun setRingerMode(audioManager: AudioManager, mode: Int) {
        /*
    // Proposed solutions that don't really work:
    1.
        // setting the ringer mode RINGER_MODE_SILENT via the audioManager api will cause
        // the phone to both set the ringer mode to silent but also turn on DND
        // we do not want DND mode, so we turn it off after 25 milliseconds.

        // why not instantly? For some reason there has to be a delay for it to work ¯\_(ツ)_/¯
        if (mode == AudioManager.RINGER_MODE_SILENT) {
            Timer().schedule(50) {

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                // Check if DND mode is currently enabled
                if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
                    || notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                ) {
                    // Turn off DND mode
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        }

       2.
                // doesn't show up in UI :/ https://issuetracker.google.com/issues/269956237
               audioManager.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE,  AudioManager.FLAG_ALLOW_RINGER_MODES or AudioManager.FLAG_SHOW_UI)
         */


        if (audioManager.ringerMode == mode) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            return
        }


        audioManager.ringerMode = mode
    }

    private val powerButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF || intent?.action == Intent.ACTION_SCREEN_ON) {

                val currentTime = System.currentTimeMillis()

                if(isPowerButtonPressed == buttonClicks && currentTime - powerButtonPressTime > 250 ) {
                    isPowerButtonPressed = 1
                    return
                }

                if (isPowerButtonPressed == buttonClicks && currentTime - powerButtonPressTime < 250 && context != null) {

                    toggleSilentMode(context)
                    isPowerButtonPressed = 1
                    return
                }

                isPowerButtonPressed += 1
                powerButtonPressTime = currentTime
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_STICKY
    }


    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        registerReceiver(powerButtonReceiver, filter)

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                "foreground",
                "Service Notification",
                NotificationManager.IMPORTANCE_NONE
            )
        notificationManager.createNotificationChannel(channel)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, "foreground")


        val notification: Notification = Notification.Builder(this, "foreground")
            .setContentTitle("Shortcut listener service")
            .setContentText("click to this to disable it")
            .setOngoing(false)
            .setAutoCancel(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerButtonReceiver)
    }
}

