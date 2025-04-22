package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer

class BluetoothBleAdvertiser(var context: Context, private var bluetoothBle: BluetoothBle) : Closeable {
    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothBle.bluetoothManager.adapter.bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        val bluetoothAdapter = bluetoothBle.bluetoothManager.adapter
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            bluetoothBle.bluetoothManager.adapter.bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        } else {
            Log.w("BluetoothBleAdvertiser", "Cannot stop advertising: Bluetooth is off.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val deviceUuid = bluetoothBle.getDeviceUuid()
        val advertiseData =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(bluetoothBle.serviceUUID))
                .addServiceData(
                    ParcelUuid(bluetoothBle.serviceUUID),
                    ByteBuffer
                        .allocate(16)
                        .putLong(deviceUuid.mostSignificantBits)
                        .putLong(deviceUuid.leastSignificantBits)
                        .array(),
                )
                .build()

        val advertiseSettings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build()

        Log.d("BluetoothBleAdvertiser", "Start advertising")
        bluetoothBle.bluetoothManager.adapter.bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    private val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("BluetoothBleAdvertiser", "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("BluetoothBleAdvertiser", "Advertising failed with error: $errorCode")
            }
        }
}
