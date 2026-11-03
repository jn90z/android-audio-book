package com.example.integrations.bluetooth

data class BleDevice(val id: String, val name: String?, val rssi: Int)
interface BleController {
    fun scan(onDevice: (BleDevice) -> Unit)
    fun stopScan()
    fun connect(deviceId: String)
    fun disconnect()
    fun write(serviceUuid: String, characteristicUuid: String, bytes: ByteArray)
}
