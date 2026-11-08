package com.atharok.btremote.presentation.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.NOTIFICATION_CHANNEL_ID
import com.atharok.btremote.common.utils.NOTIFICATION_ID
import com.atharok.btremote.domain.usecases.BluetoothHidServiceUseCase
import com.atharok.btremote.presentation.activities.MainActivity
import com.atharok.btremote.presentation.glance.MediaControlWidget
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_BACK
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_DISCONNECT
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_DOWN
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_HOME
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_LEFT
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_MULTIMEDIA_NEXT
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_MULTIMEDIA_PLAY_PAUSE
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_MULTIMEDIA_PREVIOUS
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_PICK
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_RIGHT
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_UP
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_VOLUME_DOWN
import com.atharok.btremote.presentation.services.NotificationBroadcastReceiver.Companion.ACTION_VOLUME_UP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BluetoothHidService : Service() {
    private val useCase: BluetoothHidServiceUseCase by inject()
    private lateinit var notificationManager: NotificationManager
    private var notificationChannelReady = false
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Unhandled service coroutine failure", throwable)
            }
    )
    private var connectionStateJob: Job? = null
    private var profileStartJob: Job? = null
    private var currentConnectionState = STATE_DISCONNECTED

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        connectionStateJob = serviceScope.launch {
            useCase.getDeviceHidConnectionState().collect { connection ->
                try {
                    when (connection.state) {
                        STATE_CONNECTED -> {
                            if (currentConnectionState != STATE_CONNECTED) {
                                val showButtons = useCase.showRemoteButtonsInNotification().first()
                                updateNotificationForConnectedState(connection.deviceName, showButtons)
                                currentConnectionState = STATE_CONNECTED
                            }
                            updateWidgetSafely()
                        }

                        STATE_DISCONNECTED -> {
                            if (currentConnectionState != STATE_DISCONNECTED) {
                                updateNotificationForDisconnectedState()
                                currentConnectionState = STATE_DISCONNECTED
                            }
                            updateWidgetSafely()
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to update service UI for Bluetooth state", exception)
                }
            }
        }
    }

    override fun onDestroy() {
        connectionStateJob?.cancel()
        profileStartJob?.cancel()
        serviceScope.cancel()
        stopBluetoothHidProfile()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!notificationChannelReady) {
            Log.e(TAG, "Cannot start HID service without a notification channel")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            if (profileStartJob?.isActive != true) {
                profileStartJob = serviceScope.launch {
                    try {
                        useCase.startHidProfile()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to start Bluetooth HID profile", exception)
                        stopSelf(startId)
                    }
                }
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to start foreground Bluetooth service", exception)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    // ---- Notification ----

    private fun createNotificationChannel() {
        try {
            notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
            notificationChannelReady = true
        } catch (exception: RuntimeException) {
            notificationChannelReady = false
            Log.e(TAG, "Failed to create Bluetooth service notification channel", exception)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.notification_icon)
            setContentTitle(getString(R.string.connected_off))
            setOngoing(true)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.round_launch_24,
                    getString(R.string.open),
                    createOpenApplicationPendingIntent()
                ).build()
            )
            setPriority(NotificationCompat.PRIORITY_MIN)
            setVibrate(longArrayOf(0))
        }.build()
    }

    private fun updateNotificationForConnectedState(
        deviceName: String,
        showRemoteButtonsInNotification: Boolean
    ) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.notification_icon)
            if (showRemoteButtonsInNotification) {
                setContentText(getString(R.string.connected_on, deviceName))
                setCustomBigContentView(configureRemoteViews())
            } else {
                setContentTitle(getString(R.string.connected_on, deviceName))
            }
            setOngoing(true)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.round_launch_24,
                    getString(R.string.open),
                    createOpenApplicationPendingIntent()
                ).build()
            )
            addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.round_link_off_24,
                    getString(R.string.disconnect),
                    createPendingIntent(ACTION_DISCONNECT)
                ).build()
            )
            setPriority(NotificationCompat.PRIORITY_MIN)
            setVibrate(longArrayOf(0))
        }.build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationForDisconnectedState() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private suspend fun updateWidgetSafely() {
        try {
            MediaControlWidget().updateAll(this@BluetoothHidService)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to update media control widget", exception)
        }
    }

    private fun configureRemoteViews(): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_large_layout).apply {
            setOnClickPendingIntent(R.id.notification_large_layout_volume_inc, createPendingIntent(ACTION_VOLUME_UP))
            setOnClickPendingIntent(R.id.notification_large_layout_volume_dec, createPendingIntent(ACTION_VOLUME_DOWN))
            setOnClickPendingIntent(R.id.notification_large_layout_play_pause, createPendingIntent(ACTION_MULTIMEDIA_PLAY_PAUSE))
            setOnClickPendingIntent(R.id.notification_large_layout_previous, createPendingIntent(ACTION_MULTIMEDIA_PREVIOUS))
            setOnClickPendingIntent(R.id.notification_large_layout_next, createPendingIntent(ACTION_MULTIMEDIA_NEXT))
            setOnClickPendingIntent(R.id.notification_large_layout_left, createPendingIntent(ACTION_LEFT))
            setOnClickPendingIntent(R.id.notification_large_layout_right, createPendingIntent(ACTION_RIGHT))
            setOnClickPendingIntent(R.id.notification_large_layout_up, createPendingIntent(ACTION_UP))
            setOnClickPendingIntent(R.id.notification_large_layout_down, createPendingIntent(ACTION_DOWN))
            setOnClickPendingIntent(R.id.notification_large_layout_pick, createPendingIntent(ACTION_PICK))
            setOnClickPendingIntent(R.id.notification_large_layout_back, createPendingIntent(ACTION_BACK))
            setOnClickPendingIntent(R.id.notification_large_layout_home, createPendingIntent(ACTION_HOME))
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NotificationBroadcastReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createOpenApplicationPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    // ---- Bluetooth HID Profile ----

    private fun stopBluetoothHidProfile() {
        try {
            useCase.stopHidProfile()
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to stop Bluetooth HID profile cleanly", exception)
        }
    }

    companion object {
        private const val TAG = "BluetoothHidService"

        fun start(context: Context): Boolean {
            val serviceIntent = Intent(context, BluetoothHidService::class.java)
            return try {
                context.startForegroundService(serviceIntent)
                true
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Android rejected foreground service start", exception)
                false
            }
        }

        fun stop(context: Context) {
            val serviceIntent = Intent(context, BluetoothHidService::class.java)
            try {
                context.stopService(serviceIntent)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Failed to stop Bluetooth HID service", exception)
            }
        }
    }
}
