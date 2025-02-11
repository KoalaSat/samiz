package com.koalasat.samiz.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.koalasat.samiz.R
import com.koalasat.samiz.bluethooth.BluetoothBle
import java.util.Timer

class SynchronizationService : Service() {
    private var channelSyncId = "SyncConnections"

    private val timer = Timer()

    private val binder = BluetoothBinder()
    private val bluetoothBle = BluetoothBle(this)

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startService()

        return START_STICKY
    }

    private fun startService() {
        try {
            Log.d("NotificationsService", "Starting foreground service...")
            startForeground(1, createNotification())
            bluetoothBle.start()
            keepAlive()
        } catch (e: Exception) {
            Log.e("NotificationsService", "Error in service", e)
        }
    }

    private fun keepAlive() {
//        timer.schedule(
//            object : TimerTask() {
//                override fun run() {
//                    Log.d("Samiz", "Keeping alive")
//                }
//            },
//            5000,
//            61000,
//        )
    }

    inner class BluetoothBinder : Binder() {
        fun getService(): SynchronizationService = this@SynchronizationService
    }

    private fun createNotification(): Notification {
        val notificationManager = NotificationManagerCompat.from(this)

        Log.d("NotificationsService", "Building groups...")
        val group =
            NotificationChannelGroupCompat.Builder("ServiceGroup")
                .setName(getString(R.string.service))
                .setDescription(getString(R.string.samiz_is_running_in_background))
                .build()

        notificationManager.createNotificationChannelGroup(group)

        Log.d("NotificationsService", "Building channels...")
        val channelRelays =
            NotificationChannelCompat.Builder(channelSyncId, NotificationManager.IMPORTANCE_DEFAULT)
                .setName(getString(R.string.sync_service))
                .setSound(null, null)
                .setGroup(group.id)
                .build()

        notificationManager.createNotificationChannel(channelRelays)

        Log.d("NotificationsService", "Building notification...")
        val notificationBuilder =
            NotificationCompat.Builder(this, channelSyncId)
                .setContentTitle(getString(R.string.samiz_is_running_in_background))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setGroup(group.id)
                .setSmallIcon(R.drawable.ic_launcher_foreground)

        return notificationBuilder.build()
    }
}
