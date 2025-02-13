package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.location.Address
import android.os.Message
import android.util.Log
import java.io.Closeable
import java.util.UUID
import kotlin.collections.set

interface BluetoothBleCallback {
    fun onConnection(bluetoothBle: BluetoothBle, device: BluetoothDevice)
    fun onReadResponse(bluetoothBle: BluetoothBle, device: BluetoothDevice, message: ByteArray)
    fun onReadRequest(bluetoothBle: BluetoothBle, device: BluetoothDevice): ByteArray?
    fun onWriteRequest(bluetoothBle: BluetoothBle, device: BluetoothDevice, message: ByteArray)
    fun onWriteSuccess(bluetoothBle: BluetoothBle, device: BluetoothDevice)
}

@SuppressLint("MissingPermission")
class BluetoothBle(var context: Context, private val callback: BluetoothBleCallback) : Closeable {
    val serviceUUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val readCharacteristicUUID = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
    val writeCharacteristicUUID = UUID.fromString("87654321-0000-1000-8000-00805f9b34fb")

    var mtuSize = 512

    private val deviceConnections = mutableMapOf<String, Long>()
    private var deviceReadCharacteristic = HashMap<String, BluetoothGattCharacteristic>()
    private var deviceWriteCharacteristic = HashMap<String, BluetoothGattCharacteristic>()

    lateinit var bluetoothManager: BluetoothManager
    private var bluetoothBleAdvertiser = BluetoothBleAdvertiser(this)
    private var bluetoothBleScanner = BluetoothBleScanner(this, object : BluetoothBleScannerCallback {
        override fun onDeviceFound(device: BluetoothDevice) {
            connectToDevice(device)
        }
    })
    var bluetoothBleClient = BluetoothBleClient(this, object : BluetoothBleClientCallback {
        override fun onDisconnection(device: BluetoothDevice) {
            deviceReadCharacteristic.remove(device.address)
            deviceWriteCharacteristic.remove(device.address)
        }

        override fun onCharacteristicDiscovered(device: BluetoothDevice, characteristics: BluetoothGattCharacteristic) {

            Log.d("BluetoothBle", "Characteristic discovered")
            if (characteristics.uuid == readCharacteristicUUID) {
                deviceReadCharacteristic[device.address] = characteristics
                Log.d("BluetoothBle", "READ")
            } else if (characteristics.uuid == writeCharacteristicUUID) {
                deviceWriteCharacteristic[device.address] = characteristics
                Log.d("BluetoothBle", "WRITE")
            } else {
                Log.e("BluetoothBle", "OTHER")
            }
            callback.onConnection(this@BluetoothBle, device)
        }

        override fun onReadResponse(
            device: BluetoothDevice,
            message: ByteArray
        ) {
            callback.onReadResponse(this@BluetoothBle, device, message)
        }

        override fun onWriteSuccess(device: BluetoothDevice) {
            callback.onWriteSuccess(this@BluetoothBle, device)
        }
    })

    var bluetoothBleServer = BluetoothBleServer(this, object : BluetoothBleServerCallback {
        override fun onReadRequest(device: BluetoothDevice, characteristics: BluetoothGattCharacteristic): ByteArray? {
            deviceReadCharacteristic[device.address] = characteristics
            return callback.onReadRequest(this@BluetoothBle, device)
        }

        override fun onWriteRequest(device: BluetoothDevice, characteristics: BluetoothGattCharacteristic, message: ByteArray) {
            deviceWriteCharacteristic[device.address] = characteristics
            return callback.onWriteRequest(this@BluetoothBle, device, message)
        }
    })

    fun start() {
        bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        if (bluetoothManager.adapter == null || !bluetoothManager.adapter.isEnabled) {
            Log.e("BluetoothBle", "Bluetooth not enabled")
            return
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
            Log.d("BluetoothBleServer", "${device.address} - Sending read message")
            bluetoothBleClient.readCharacteristic(device, characteristic)
        } else {
            Log.e("BluetoothBle", "${device.address} - Characteristic not found")
        }
    }

    fun writeMessage(device: BluetoothDevice, message: ByteArray) {
        val characteristic = deviceWriteCharacteristic[device.address]
        if (characteristic != null) {
            bluetoothBleClient.sendWriteMessage(device, characteristic, message)
        } else {
            Log.e("BluetoothBle", "${device.address} - Characteristic not found")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        val now = System.currentTimeMillis()

        val lastCalledTime = deviceConnections[device.address]
        if (lastCalledTime != null && (now - lastCalledTime) < 300000) {
            return
        }
        deviceConnections[device.address] = now

        val gatt = device.connectGatt(context, false, bluetoothBleClient.bluetoothGattCallback)
        val address = gatt?.device?.address
        Log.d("BluetoothBle", "$address - Connecting to device: ${device.name} - ${device.address}")
    }

}