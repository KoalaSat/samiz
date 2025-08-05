package com.koalasat.samiz.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.koalasat.samiz.R
import com.koalasat.samiz.bluethooth.BluetoothReconciliation
import com.koalasat.samiz.model.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer

class SynchronizationService : Service() {
    private var channelSyncId = "SyncConnections"

    private val timer = Timer()

    private val binder = BluetoothBinder()
    private val bluetoothReconciliation = BluetoothReconciliation(this)

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

    override fun onDestroy() {
        super.onDestroy()
        bluetoothReconciliation.close()
    }

    private fun startService() {
        try {
            Logger.d("SynchronizationService", "Starting foreground service...")
            startForeground(1, createNotification())
            CoroutineScope(Dispatchers.IO).launch {
                bluetoothReconciliation.start()
            }
            keepAlive()
        } catch (e: Exception) {
            Logger.e("SynchronizationService", "Error in service: $e")
        }
    }

    private fun keepAlive() {
//        timer.schedule(
//            object : TimerTask() {
//                override fun run() {
//                    Logger.d("Samiz", "Keeping alive")
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

        Logger.d("SynchronizationService", "Building groups...")
        val group =
            NotificationChannelGroupCompat.Builder("ServiceGroup")
                .setName(getString(R.string.service))
                .setDescription(getString(R.string.samiz_is_running_in_background))
                .build()

        notificationManager.createNotificationChannelGroup(group)

        Logger.d("SynchronizationService", "Building channels...")
        val channelRelays =
            NotificationChannelCompat.Builder(channelSyncId, NotificationManager.IMPORTANCE_DEFAULT)
                .setName(getString(R.string.sync_service))
                .setSound(null, null)
                .setGroup(group.id)
                .build()

        notificationManager.createNotificationChannel(channelRelays)

        Logger.d("SynchronizationService", "Building notification...")
        val notificationBuilder =
            NotificationCompat.Builder(this, channelSyncId)
                .setContentTitle(getString(R.string.samiz_is_running_in_background))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setGroup(group.id)
                .setSmallIcon(R.drawable.ic_launcher_foreground)

        return notificationBuilder.build()
    }
}
