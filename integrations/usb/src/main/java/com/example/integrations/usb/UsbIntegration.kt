package com.example.integrations.usb

data class UsbDeviceInfo(val vendorId: Int, val productId: Int, val name: String?)
interface UsbIntegration {
    fun devices(): List<UsbDeviceInfo>
    fun open(device: UsbDeviceInfo): Boolean
    fun write(bytes: ByteArray): Int
    fun read(maxBytes: Int): ByteArray
    fun close()
}
