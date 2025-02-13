package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.io.Closeable
import kotlin.collections.set

interface BluetoothBleClientCallback {
    fun onDisconnection(device: BluetoothDevice)
    fun onCharacteristicDiscovered(device: BluetoothDevice, characteristics: BluetoothGattCharacteristic)
    fun onReadResponse(device: BluetoothDevice, message: ByteArray)
    fun onWriteSuccess(device: BluetoothDevice)
}

class BluetoothBleClient(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleClientCallback) : Closeable {

    private var deviceGatt = HashMap<String, BluetoothGatt>()
    private var initialMessages = HashMap<String, Array<ByteArray>>()

    @SuppressLint("MissingPermission")
    override fun close() {
        deviceGatt.values.forEach { it.close() }
    }

    val bluetoothGattCallback =
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
                        gatt.requestMtu(bluetoothBle.mtuSize)
                        gatt.discoverServices()
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.d("BluetoothBle", "$address - Disconnected from GATT server")
                        deviceGatt.remove(address)
                        callback.onDisconnection(gatt.device)
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
                    val service = gatt.getService(bluetoothBle.serviceUUID)
                    if (service == null || address == null) {
                        Log.d("BluetoothBle", "$address - Service not existing")
                    } else {
                        Log.d("BluetoothBle", "$address - Discovered Service: ${service.uuid}")
                        val readCharacteristic = service.getCharacteristic(bluetoothBle.readCharacteristicUUID)
                        if (readCharacteristic != null) {
                            Log.d("BluetoothBle", "$address - Read characteristic")
                            deviceGatt[address] = gatt
                            callback.onCharacteristicDiscovered(gatt.device, readCharacteristic)
                        }
                        val writeCharacteristic = service.getCharacteristic(bluetoothBle.writeCharacteristicUUID)
                        if (writeCharacteristic != null) {
                            Log.d("BluetoothBle", "$address - Read characteristic")
                            deviceGatt[address] = gatt
                            callback.onCharacteristicDiscovered(gatt.device, writeCharacteristic)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                // Handle the response message
                Log.d("BluetoothBle", "Characteristic changed")
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)

                val device = gatt?.device
                val address = device?.address
                if (status == BluetoothGatt.GATT_SUCCESS && address != null) {
                    deviceGatt[address] = gatt
                    Log.d("BluetoothBle", "$address - Write successful")
                    callback.onWriteSuccess(device)
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
                val device = gatt?.device
                val address = device?.address
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null && address != null) {
                    deviceGatt[address] = gatt
                    Log.d("BluetoothBle", "$address - Read response")
                    var jsonBytes = characteristic.getValue()
                    if (jsonBytes != null) {
                        var message = processReadMessage(gatt, characteristic, jsonBytes)
                        if (message != null) {
                            Log.d("BluetoothBleServer", "$address - Read message received : $message")
                            callback.onReadResponse(device, message)
                        } else {
                            Log.e("BluetoothBleServer", "$address - Invalid Read")
                        }
                    }
                } else {
                    Log.d("BluetoothBle", "$address - Read failed with status $status")
                }
            }
        }


    @SuppressLint("MissingPermission")
    private fun processReadMessage(gatt: BluetoothGatt?,
                                      characteristic: BluetoothGattCharacteristic?,
                                      jsonBytes: ByteArray) : ByteArray? {
        val address = gatt?.device?.address

        if (characteristic != null && address != null) {
            val chunkIndex = jsonBytes[0].toByte()

            Log.d("BluetoothBle", "$address - Received chunk ${chunkIndex + 1}")
            initialMessages[address] = initialMessages.getOrDefault(address, emptyArray()) + jsonBytes

            val isLastChunk = jsonBytes[jsonBytes.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBle", "$address - Last chunk received")
                var chunks = initialMessages.getOrDefault(address, emptyArray())
                val decompressMessage = joinChunks(chunks)
                initialMessages.remove(address)
                return decompressMessage
            } else {
                gatt.readCharacteristic(characteristic)
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun sendWriteMessage(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, message: ByteArray) {
        Log.d("BluetoothBle", "${device.address} - Sending write message")
        val gatt = deviceGatt[device.address]
        var chunks = splitInChunks(message)
        Log.d("BluetoothBle", "${device.address} - Split into ${chunks.size} chunks")
        if (gatt != null) {
            chunks.forEachIndexed { index, chunk ->
                characteristic.value = chunk
                Log.d("BluetoothBle", "${device.address} - Sending chunk $index")
                gatt.writeCharacteristic(characteristic)
            }
            Log.d("BluetoothBle", "${device.address} - Sent write message $message")
        } else {
            Log.e("BluetoothBle", "${device.address} - Gatt not found")
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        val gatt = deviceGatt[device.address]
        val address = gatt?.device?.address
        if (gatt != null) {
            Log.d("BluetoothBle", "$address - Read sent")
            gatt.readCharacteristic(characteristic)
        } else {
            Log.e("BluetoothBle", "$address - Gatt not found")
        }
    }
}