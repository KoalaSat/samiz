package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
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
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.os.ParcelUuid
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.compressByteArray
import com.koalasat.samiz.util.Compression.Companion.decompressByteArray
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.io.Closeable
import java.nio.charset.Charset
import java.util.UUID
import kotlin.collections.plus
import kotlin.collections.set

@SuppressLint("MissingPermission")
class BluetoothBle(var context: Context) : Closeable {
    val serviceUUID = UUID.fromString("880527f8-4fa7-4b3b-894f-56790ef5bf57")
    val characteristicUUID = UUID.fromString("57dfd4ce-8694-4e80-bc56-11fc7a735df0")
    var mtuSize = 512

    var bluetoothBleServer = BluetoothBleServer(this)
    lateinit var bluetoothManager: BluetoothManager

    private var deviceGatt = HashMap<String, BluetoothGatt>()
    private var bluetoothBleAdvertiser = BluetoothBleAdvertiser(this)
    private var bluetoothBleScanner = BluetoothBleScanner(this)

    private val deviceConnections = mutableMapOf<String, Long>()
    private var inputReadMessages = HashMap<String, Array<ByteArray>>()

    fun start() {
        bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        if (!bluetoothManager.adapter.isEnabled) {
            bluetoothManager.adapter.enable()
        }

        bluetoothBleServer.createGattServer()
        bluetoothBleAdvertiser.startAdvertising()
        bluetoothBleScanner.startScanning()
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        deviceGatt.values.forEach { it.close() }
    }


    @SuppressLint("MissingPermission")
     fun connectToDevice(device: BluetoothDevice) {
        val now = System.currentTimeMillis()

        val lastCalledTime = deviceConnections[device.address]
        if (lastCalledTime != null && (now - lastCalledTime) < 300000) {
            return
        }
        deviceConnections[device.address] = now

        val gatt = device.connectGatt(context, false, bluetoothGattCallback)
        val address = gatt?.device?.address
        Log.d("BluetoothBle", "$address - Connecting to device: ${device.name} - ${device.address}")
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
                val address = gatt.device?.address
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.d("BluetoothBle", "$address - Connected to GATT server")
                        gatt.requestMtu(mtuSize)
                        gatt.discoverServices()
                        if (address != null) {
                            deviceGatt[address] = gatt
                        } else {
                            Log.e("BluetoothBle", "Device address not found")
                        }

                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.d("BluetoothBle", "$address - Disconnected from GATT server")
                        deviceGatt.remove(address)
                        gatt.close()
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                super.onServicesDiscovered(gatt, status)
                val address = gatt.device?.address
                Log.d("BluetoothBle", "$address - GATT Status: $status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(serviceUUID)
                    if (service == null) {
                        Log.d("BluetoothBle", "$address - Service not existing")
                    } else {
                        Log.d("BluetoothBle", "$address - Discovered Service: ${service.uuid}")
                        val characteristic = service.getCharacteristic(characteristicUUID)
                        if (characteristic != null) {
                            Log.d("BluetoothBle", "$address - Reading characteristic")
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
                Log.d("BluetoothBle", "Characteristic changed : $responseMessage")
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)

                val address = gatt?.device?.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BluetoothBle", "$address - Write successful")
                } else {
                    Log.d("BluetoothBle", "$address - Write failed with status $status")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                val address = gatt?.device?.address
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null && address != null) {
                    var jsonBytes = characteristic.getValue()
                    if (jsonBytes != null) {
                        val chunkIndex = jsonBytes[0].toByte()

                        Log.d("BluetoothBle", "$address - Received chunk $chunkIndex")
                        inputReadMessages[address] = inputReadMessages.getOrDefault(address, emptyArray()) + jsonBytes

                        val isLastChunk = jsonBytes[jsonBytes.size - 1] == 1.toByte()
                        if (isLastChunk) {
                            Log.d("BluetoothBle", "$address - Last chunk received")
                            bluetoothBleServer.clearOutputData(address)
                        }

                        if (isLastChunk) {
                            var chunks = inputReadMessages.getOrDefault(address, emptyArray())
                            val jointChunks = joinChunks(chunks)
                            val decompressMessage = decompressByteArray(jointChunks)
                            var data = String(decompressMessage, Charset.forName("UTF-8"))
                            Log.d("BluetoothBle", "$address - Read successful: $data : size ${data.length}")
                        } else {
                            gatt.readCharacteristic(characteristic)
                        }
                    }
                } else {
                    Log.d("BluetoothBle", "$address - Read failed with status $status")
                }
            }
        }
}