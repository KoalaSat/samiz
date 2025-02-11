package com.koalasat.samiz.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.koalasat.samiz.R
import com.koalasat.samiz.util.Compression.Companion.compressByteArray
import com.koalasat.samiz.util.Compression.Companion.decompressByteArray
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.nio.charset.Charset
import java.util.Timer
import java.util.UUID
import kotlin.collections.getOrDefault

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
    private var mtuSize = 512

    private var outputReadMessages = HashMap<String, Array<ByteArray>>()
    private var inputReadMessages = HashMap<String, Array<ByteArray>>()

    // ////////////////////////////////////////////////////////////////// SERVICE

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

    // ////////////////////////////////////////////////////////////////// ADVERTISING

    @SuppressLint("MissingPermission")
    private fun createGattServer() {
        bluetoothGattServer =
            bluetoothManager.openGattServer(
                this,
                object : BluetoothGattServerCallback() {
                    override fun onServiceAdded(
                        status: Int,
                        service: BluetoothGattService,
                    ) {
                        super.onServiceAdded(status, service)
                    }

                    override fun onCharacteristicReadRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        offset: Int,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                        if (characteristic.uuid.equals(characteristicUUID)) {
                            val jsonBytes =
                                if (outputReadMessages.containsKey(device.address)) {
                                    inputReadMessages.getOrDefault(device.address, emptyArray())
                                } else {
                                    var message = "{\"id\":\"4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65\",\"pubkey\":\"6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93\",\"created_at\":1673347337,\"kind\":1,\"tags\":[[\"e\",\"3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206\"],[\"p\",\"bf2376e17ba4ec269d10fcc996a4746b451152be9031fa48e74553dde5526bce\"]],\"content\":\"Walledgardensbecameprisons,andnostristhefirststeptowardstearingdowntheprisonwalls.\",\"sig\":\"908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262\"}"
                                    var compressedData = compressByteArray(message.toByteArray(Charset.forName("UTF-8")))
                                    var chunks = splitInChunks(compressedData)

                                    Log.d("NotificationsService", "Created ${chunks.size} chunks")
                                    chunks
                                }

                            if (jsonBytes.isNotEmpty()) {
                                Log.d("NotificationsService", "Sending Chunk ${jsonBytes.size}")
                                val nextChunk = jsonBytes.first()
                                outputReadMessages[device.address] = jsonBytes.copyOfRange(1, jsonBytes.size)

                                sendData(device, requestId, characteristic, nextChunk)

                                val isLastChunk = nextChunk[nextChunk.size - 1] == 1.toByte()
                                if (isLastChunk) {
                                    Log.d("NotificationsService", "Last chunk sent")
                                    outputReadMessages.remove(device.address)
                                }
                            }
                        } else {
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                        }
                    }

                    override fun onCharacteristicWriteRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        characteristic: BluetoothGattCharacteristic,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray,
                    ) {
                        super.onCharacteristicWriteRequest(
                            device,
                            requestId,
                            characteristic,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value,
                        )
                        // Get the value of the characteristic
                        val message = String(value)

                        Log.d(
                            "NotificationsService",
                            "Write request from ${device.name} : $message",
                        )
                    }
                },
            )
    }

    @SuppressLint("MissingPermission")
    fun sendData(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        jsonBytes: ByteArray,
    ) {
        var offset = 0

        while (offset < jsonBytes.size) {
            val chunkSize = minOf(mtuSize, jsonBytes.size + offset)
            val chunk = jsonBytes.copyOfRange(offset, minOf(offset + chunkSize, jsonBytes.size))
            characteristic.value = chunk
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            offset += chunkSize
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic =
            BluetoothGattCharacteristic(
                characteristicUUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        service.addCharacteristic(characteristic)
        bluetoothGattServer.addService(service)

        val advertiseData =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(serviceUUID))
                .build()

        val advertiseSettings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        bluetoothManager.adapter.bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    private val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("NotificationsService", "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("NotificationsService", "Advertising failed with error: $errorCode")
            }
        }

    // ///////////////////////////////////////////////////////////////// SCANNING

    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        val scanFilter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()

        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

        bluetoothManager.adapter.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                super.onScanResult(callbackType, result)
                val device = result.device
                connectToDevice(device)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BluetoothService", "Scan failed with error code: $errorCode")
            }
        }

    // //////////////////////////////////////////////////// COMMUNICATION

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

    private val bluetoothGattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.d("NotificationsService", "Connected to GATT server")
                        gatt.requestMtu(mtuSize)
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
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
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

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
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
                status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("NotificationsService", "Write successful")
                } else {
                    Log.d("NotificationsService", "Write failed with status $status")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    var jsonBytes = characteristic.getValue()
                    if (jsonBytes != null && gatt?.device?.address != null) {
                        val address = gatt.device.address
                        val chunkIndex = jsonBytes[0].toByte()

                        Log.d("NotificationsService", "Received chunk $chunkIndex")
                        inputReadMessages[address] = inputReadMessages.getOrDefault(address, emptyArray()) + jsonBytes

                        val isLastChunk = jsonBytes[jsonBytes.size - 1] == 1.toByte()
                        if (isLastChunk) {
                            Log.d("NotificationsService", "Last chunk received")
                            outputReadMessages.remove(address)
                        }

                        if (isLastChunk) {
                            var chunks = inputReadMessages.getOrDefault(address, emptyArray())
                            val jointChunks = joinChunks(chunks)
                            val decompressMessage = decompressByteArray(jointChunks)
                            var data = String(decompressMessage, Charset.forName("UTF-8"))
                            Log.d("NotificationsService", "Read successful: $data : size ${data.length}")
                        } else {
                            gatt.readCharacteristic(characteristic)
                        }
                    }
                } else {
                    Log.d("NotificationsService", "Read failed with status $status")
                }
            }
        }

    // ///////////////////////////////////////////////////////////////// NOTIFICATION

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
