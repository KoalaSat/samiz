package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log
import java.io.Closeable

class BluetoothBleAdvertiser(private var bluetoothBle: BluetoothBle) : Closeable {

    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothBle.bluetoothManager.adapter.bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val service = BluetoothGattService(bluetoothBle.serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val readCharacteristic = BluetoothGattCharacteristic(
            bluetoothBle.readCharacteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val writeCharacteristic = BluetoothGattCharacteristic(
            bluetoothBle.writeCharacteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        writeCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        service.addCharacteristic(readCharacteristic)
        service.addCharacteristic(writeCharacteristic)
        bluetoothBle.addService(service)

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
                Log.d("BluetoothBleAdvertiser", "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("BluetoothBleAdvertiser", "Advertising failed with error: $errorCode")
            }
        }
}