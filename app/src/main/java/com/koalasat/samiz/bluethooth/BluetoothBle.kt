package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.util.Log
import java.io.Closeable
import java.util.UUID
import kotlin.collections.set

@SuppressLint("MissingPermission")
class BluetoothBle(var context: Context) : Closeable {
    val serviceUUID = UUID.fromString("880527f8-4fa7-4b3b-894f-56790ef5bf57")
    val characteristicUUID = UUID.fromString("57dfd4ce-8694-4e80-bc56-11fc7a735df0")
    var mtuSize = 512

    lateinit var bluetoothManager: BluetoothManager

    var bluetoothBleServer = BluetoothBleServer(this)
    var bluetoothBleClient = BluetoothBleClient(this)
    private var bluetoothBleAdvertiser = BluetoothBleAdvertiser(this)
    private var bluetoothBleScanner = BluetoothBleScanner(this)

    private val deviceConnections = mutableMapOf<String, Long>()

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


    @SuppressLint("MissingPermission")
     fun connectToDevice(device: BluetoothDevice) {
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

    fun addService(service: BluetoothGattService) {
        bluetoothBleServer.addService(service)
    }
}