package com.koalasat.samiz.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.koalasat.samiz.R
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class SynchronizationService : Service() {
    private var channelSyncId = "SyncConnections"

    private val timer = Timer()
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothGattServer: BluetoothGattServer

    private val binder = BluetoothBinder()
    private val serviceUUID = UUID.fromString("880527f8-4fa7-4b3b-894f-56790ef5bf57")
    private val characteristicUUID = UUID.fromString("57dfd4ce-8694-4e80-bc56-11fc7a735df0")

    private val deviceConnections = mutableMapOf<String, Long>()
    private var gatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private var request = false

    //////////////////////////////////////////////////////////////////// SERVICE

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()

        return START_STICKY
    }

    private fun startService() {
        try {
            Log.d("NotificationsService", "Starting foreground service...")
            startForeground(1, createNotification())
            keepAlive()
        } catch (e: Exception) {
            Log.e("NotificationsService", "Error in service", e)
        }
    }

    private fun keepAlive() {
        startAdvertising()
//        timer.schedule(
//            object : TimerTask() {
//                override fun run() {
//                    Log.d("Samiz", "Keeping alive")
                    scanForDevices()
//                }
//            },
//            5000,
//            61000,
//        )
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        if (!bluetoothManager.adapter.isEnabled) {
            bluetoothManager.adapter.enable()
        }

        createGattServer()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }

    inner class BluetoothBinder : Binder() {
        fun getService(): SynchronizationService = this@SynchronizationService
    }

    //////////////////////////////////////////////////////////////////// ADVERTISING

    @SuppressLint("MissingPermission")
    private fun createGattServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                super.onServiceAdded(status, service)
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Log.d("NotificationsService", "New Read Request from ${device.name} : offset $offset")
                val jsonArray = "[{\"key\":\"value\"},{\"key\":\"value\"}]"
                val jsonBytes = jsonArray.toByteArray()
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, jsonBytes.sliceArray(offset until jsonBytes.size))
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                // Get the value of the characteristic
                val message = String(value)

                Log.d(
                    "NotificationsService",
                    "Write request from ${device.name} : $message"
                )
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(characteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
        service.addCharacteristic(characteristic)
        bluetoothGattServer.addService(service)

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUUID))
            .build()

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        bluetoothManager.adapter.bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d("NotificationsService", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("NotificationsService", "Advertising failed with error: $errorCode")
        }
    }

    /////////////////////////////////////////////////////////////////// SCANNING

    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        bluetoothManager.adapter.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BluetoothService", "Scan failed with error code: $errorCode")
        }
    }

    ////////////////////////////////////////////////////// COMMUNICATION

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        val now = System.currentTimeMillis()

        val lastCalledTime = deviceConnections[device.address]
        if (lastCalledTime != null && (now - lastCalledTime) < 300000) {
            return
        }
        deviceConnections[device.address] = now
        
        Log.d("NotificationsService", "Connecting to device: ${device.name} - ${device.address}")
        gatt = device.connectGatt(this, false, bluetoothGattCallback)
        connectedDevice = device
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d("NotificationsService", "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d("NotificationsService", "Disconnected from GATT server")
                    gatt.close()
                    connectedDevice = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d("NotificationsService", "GATT Status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(serviceUUID)
                if (service == null) {
                    Log.d("NotificationsService", "Service not existing")
                } else {
                    Log.d("NotificationsService", "Discovered Service: ${service.uuid}")
                    val characteristic = service.getCharacteristic(characteristicUUID)
                    if (characteristic != null) {
                        Log.d("NotificationsService", "Reading characteristic")
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            val response = characteristic.value
            val responseMessage = String(response)
            // Handle the response message
            println("Response from ${characteristic.service.uuid} : $responseMessage")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("NotificationsService", "Write successful")
            } else {
                Log.d("NotificationsService", "Write failed with status $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val jsonBytes = characteristic?.value
                if (jsonBytes != null) {
                    val jsonArray = String(jsonBytes, Charset.forName("UTF-8"))
                    Log.d("NotificationsService", "Read successful: $jsonArray")
                }
            } else {
                Log.d("NotificationsService", "Read failed with status $status")
            }
        }
    }

    /////////////////////////////////////////////////////////////////// NOTIFICATION

    private fun createNotification(): Notification {
        val notificationManager = NotificationManagerCompat.from(this)

        Log.d("NotificationsService", "Building groups...")
        val group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(getString(R.string.service))
            .setDescription(getString(R.string.samiz_is_running_in_background))
            .build()

        notificationManager.createNotificationChannelGroup(group)

        Log.d("NotificationsService", "Building channels...")
        val channelRelays = NotificationChannelCompat.Builder(channelSyncId, NotificationManager.IMPORTANCE_DEFAULT)
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