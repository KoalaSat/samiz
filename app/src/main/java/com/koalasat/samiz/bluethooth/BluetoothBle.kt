package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.pm.PackageManager
import android.util.Log
import com.koalasat.samiz.Samiz
import java.io.Closeable
import java.util.UUID
import kotlin.collections.set

interface BluetoothBleCallback {
    fun onConnection(
        bluetoothBle: BluetoothBle,
        device: BluetoothDevice,
    )

    fun onReadResponse(
        bluetoothBle: BluetoothBle,
        device: BluetoothDevice,
        message: ByteArray,
    )

    fun onReadRequest(
        bluetoothBle: BluetoothBle,
        device: BluetoothDevice,
    ): ByteArray?

    fun onWriteRequest(
        bluetoothBle: BluetoothBle,
        device: BluetoothDevice,
        message: ByteArray,
    )

    fun onWriteSuccess(
        bluetoothBle: BluetoothBle,
        device: BluetoothDevice,
    )
}

@SuppressLint("MissingPermission")
class BluetoothBle(var context: Context, private val callback: BluetoothBleCallback) : Closeable {
    val serviceUUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val readCharacteristicUUID = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
    val writeCharacteristicUUID = UUID.fromString("87654321-0000-1000-8000-00805f9b34fb")

    var advertiserUuidPref = "advertiser_uuid"

    var mtuSize = 512

    var devices = HashMap<String, BluetoothDevice>()
    private var deviceReadCharacteristic = HashMap<String, BluetoothGattCharacteristic>()
    private var deviceWriteCharacteristic = HashMap<String, BluetoothGattCharacteristic>()

    lateinit var bluetoothManager: BluetoothManager
    private var bluetoothBleAdvertiser = BluetoothBleAdvertiser(context, this)
    private var bluetoothBleScanner =
        BluetoothBleScanner(
            this,
            object : BluetoothBleScannerCallback {
                override fun onDeviceFound(
                    device: BluetoothDevice,
                    remoteUuid: UUID,
                ) {
                    connectToDevice(device, remoteUuid)
                }
            },
        )
    var bluetoothBleClient =
        BluetoothBleClient(
            this,
            object : BluetoothBleClientCallback {
                override fun onDisconnection(device: BluetoothDevice) {
                    deviceReadCharacteristic.remove(device.address)
                    deviceWriteCharacteristic.remove(device.address)
                    devices.remove(device.address)
                    connectToDevice(device, null)
                }

                override fun onCharacteristicDiscovered(
                    device: BluetoothDevice,
                    characteristics: BluetoothGattCharacteristic,
                ) {
                    Log.d("BluetoothBle", "${device.address} - Characteristic discovered")
                    if (characteristics.uuid == readCharacteristicUUID) {
                        deviceReadCharacteristic[device.address] = characteristics
                        devices.put(device.address, device)
                        Log.d("BluetoothBle", "${device.address} - READ Characteristic discovered")
                    } else if (characteristics.uuid == writeCharacteristicUUID) {
                        deviceWriteCharacteristic[device.address] = characteristics
                        devices.put(device.address, device)
                        Log.d("BluetoothBle", "${device.address} - WRITE Characteristic discovered")
                    } else {
                        Log.d("BluetoothBle", "${device.address} - OTHER Characteristic discovered")
                    }

                    if (deviceReadCharacteristic[device.address] != null &&
                        deviceWriteCharacteristic[device.address] != null
                    ) {
                        callback.onConnection(this@BluetoothBle, device)
                    }
                }

                override fun onReadResponse(
                    device: BluetoothDevice,
                    message: ByteArray,
                ) {
                    callback.onReadResponse(this@BluetoothBle, device, message)
                }

                override fun onWriteSuccess(device: BluetoothDevice) {
                    callback.onWriteSuccess(this@BluetoothBle, device)
                }
            },
        )

    var bluetoothBleServer =
        BluetoothBleServer(
            this,
            object : BluetoothBleServerCallback {
                override fun onReadRequest(
                    device: BluetoothDevice,
                    characteristics: BluetoothGattCharacteristic,
                ): ByteArray? {
                    Log.d("BluetoothBle", "${device.address} - READ request")
                    deviceReadCharacteristic[device.address] = characteristics
                    devices.put(device.address, device)
                    return callback.onReadRequest(this@BluetoothBle, device)
                }

                override fun onWriteRequest(
                    device: BluetoothDevice,
                    characteristics: BluetoothGattCharacteristic,
                    message: ByteArray,
                ) {
                    Log.d("BluetoothBle", "${device.address} - WRITE request")
                    deviceWriteCharacteristic[device.address] = characteristics
                    devices.put(device.address, device)
                    return callback.onWriteRequest(this@BluetoothBle, device, message)
                }
            },
        )

    fun start() {
        bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        val canBeClient: Boolean =
            bluetoothAdapter != null &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val canBeServer: Boolean =
            bluetoothAdapter != null &&
                bluetoothAdapter.bluetoothLeAdvertiser != null

        if (bluetoothAdapter == null) {
            Log.e("BluetoothBle", "Bluetooth not supported on this device")
            return
        } else if (!bluetoothAdapter.isEnabled) {
            Log.e("BluetoothBle", "Bluetooth is not enabled")
            return
        } else {
            Log.d("BluetoothBle", "Bluetooth is enabled")
        }

        if (!canBeClient || !canBeServer) {
            Log.e("BluetoothBle", "BLE is not enabled")
            return
        } else {
            Log.d("BluetoothBle", "Device supports BLE")
        }

        bluetoothBleServer.createGattServer()
        bluetoothBleAdvertiser.startAdvertising()

        bluetoothBleScanner.startScanning()
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothBleClient.close()
        bluetoothBleServer.close()

        bluetoothBleAdvertiser.close()
        bluetoothBleScanner.close()
    }

    fun addService(service: BluetoothGattService) {
        bluetoothBleServer.addService(service)
    }

    fun readMessage(device: BluetoothDevice) {
        val characteristic = deviceReadCharacteristic[device.address]
        if (characteristic != null) {
            Log.d("BluetoothBle", "${device.address} - Sending read message")
            bluetoothBleClient.readCharacteristic(device, characteristic)
        } else {
            Log.e("BluetoothBle", "${device.address} - Characteristic not found")
        }
    }

    fun writeMessage(
        device: BluetoothDevice,
        message: ByteArray,
    ) {
        val characteristic = deviceWriteCharacteristic[device.address]
        if (characteristic != null) {
            bluetoothBleClient.sendWriteMessage(device, characteristic, message)
        } else {
            Log.d("BluetoothBle", "${device.address} - Characteristic not found")
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun connectToDevice(
        device: BluetoothDevice,
        remoteUuid: UUID?,
    ) {
        Log.d("BluetoothBle", "${device.address} - connectToDevice")
        if (deviceReadCharacteristic[device.address] == null &&
            deviceWriteCharacteristic[device.address] == null
        ) {
            Samiz.updateFoundDevices(remoteUuid.toString())
            devices.put(device.address, device)
            if (remoteUuid == null || remoteUuid < getDeviceUuid()) {
                Log.d("BluetoothBle", "${device.address} - Acting as CLIENT")
                Log.d("BluetoothBle", "${device.address} - Connecting to device: ${device.address}")
                device.connectGatt(context, false, bluetoothBleClient.bluetoothGattCallback)
            } else {
                Log.d("BluetoothBle", "${device.address} - Acting as SERVER")
            }
        }
    }

    fun getDeviceUuid(): UUID {
        val prefs = context.getSharedPreferences(advertiserUuidPref, Context.MODE_PRIVATE)
        val existingUuid = prefs.getString(advertiserUuidPref, null)

        return if (existingUuid != null) {
            UUID.fromString(existingUuid)
        } else {
            val newUuid = UUID.randomUUID()
            prefs.edit().putString(advertiserUuidPref, newUuid.toString()).apply()
            newUuid
        }
    }

    fun notifyDevice() {
        devices.values.forEach {
            Log.d("BluetoothBle", "${it.address} - Sending notification")
            val characteristic = deviceReadCharacteristic[it.address]
            if (characteristic != null) {
                characteristic.value = "HOLI".toByteArray()
                bluetoothBleServer.notifyAllClients(it, characteristic)
            } else {
                Log.d("BluetoothBle", "${it.address} - Read characteristic not found")
            }
        }
    }
}
