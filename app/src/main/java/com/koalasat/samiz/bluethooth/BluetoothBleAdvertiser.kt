package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BluetoothBleAdvertiser(private var bluetoothBle: BluetoothBle) {
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val service = BluetoothGattService(bluetoothBle.serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic =
            BluetoothGattCharacteristic(
                bluetoothBle.characteristicUUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        service.addCharacteristic(characteristic)
        bluetoothBle.bluetoothBleServer.addService(service)

        val advertiseData =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(bluetoothBle.serviceUUID))
                .build()

        val advertiseSettings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        bluetoothBle.bluetoothManager.adapter.bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
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
}