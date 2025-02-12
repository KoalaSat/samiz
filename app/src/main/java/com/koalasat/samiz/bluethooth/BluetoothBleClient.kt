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

class BluetoothBleClient(private var bluetoothBle: BluetoothBle) : Closeable {

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
                    val service = gatt.getService(bluetoothBle.serviceUUID)
                    if (service == null) {
                        Log.d("BluetoothBle", "$address - Service not existing")
                    } else {
                        Log.d("BluetoothBle", "$address - Discovered Service: ${service.uuid}")
                        val characteristic = service.getCharacteristic(bluetoothBle.characteristicUUID)
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
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    Log.d("BluetoothBle", "$address - Read request")
                    var jsonBytes = characteristic.getValue()
                    if (jsonBytes != null) {
                        var message = processReadMessage(gatt, characteristic, jsonBytes)
                        if (message != null) {
                            processInitialMessage(gatt, characteristic, message)
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
    private fun processInitialMessage(gatt: BluetoothGatt?,
                                      characteristic: BluetoothGattCharacteristic?,
                                      message: ByteArray) {
        val address = gatt?.device?.address
        Log.d("BluetoothBle", "$address - Receiving initial message chunk $message ${message.size}")
        val resultClient = BluetoothReconciliation().getReconcile(message)
        if (resultClient.msg != null && gatt?.device != null && characteristic != null) {
            Log.d("BluetoothBle", "$address - Received initial message")
            sendWriteMessage(gatt.device, characteristic, resultClient.msg!!)
        } else {
            Log.e("BluetoothBle", "$address - Bad initial message")
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
        val gatt = deviceGatt[device.address]
        val address = gatt?.device?.address
        splitInChunks(message).forEach {
            characteristic.value = it
            gatt?.writeCharacteristic(characteristic)
        }
        Log.d("BluetoothBle", "$address - Sent event message $message")
    }
}