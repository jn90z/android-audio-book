package com.example.integrations.usb

import android.content.Context
import android.hardware.usb.*

class AndroidUsbIntegration(context:Context):UsbIntegration {
    private val manager=context.getSystemService(UsbManager::class.java)
    private var connection:UsbDeviceConnection?=null
    private var intf:UsbInterface?=null
    private var inEp:UsbEndpoint?=null
    private var outEp:UsbEndpoint?=null
    override fun devices()=manager.deviceList.values.map{UsbDeviceInfo(it.vendorId,it.productId,it.productName)}
    override fun open(device:UsbDeviceInfo):Boolean {
        val d=manager.deviceList.values.firstOrNull{it.vendorId==device.vendorId&&it.productId==device.productId}?:return false
        if(d.interfaceCount==0)return false
        val i=d.getInterface(0); val c=manager.openDevice(d)?:return false
        if(!c.claimInterface(i,true)){c.close();return false}; connection=c;intf=i
        for(x in 0 until i.endpointCount){val e=i.getEndpoint(x);if(e.direction==UsbConstants.USB_DIR_IN)inEp=e else outEp=e};return true
    }
    override fun write(bytes:ByteArray)=connection?.bulkTransfer(outEp,bytes,bytes.size,2000)?:-1
    override fun read(maxBytes:Int):ByteArray { val b=ByteArray(maxBytes);val n=connection?.bulkTransfer(inEp,b,b.size,2000)?:-1;return if(n>0)b.copyOf(n) else byteArrayOf() }
    override fun close(){intf?.let{connection?.releaseInterface(it)};connection?.close();connection=null}
}
