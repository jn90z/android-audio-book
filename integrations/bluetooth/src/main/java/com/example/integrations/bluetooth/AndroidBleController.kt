package com.example.integrations.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleController(private val appContext: Context) : BleController {
    private val manager=appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get()=manager.adapter
    private var callback:ScanCallback?=null
    private var gatt:BluetoothGatt?=null
    private var pending:Triple<UUID,UUID,ByteArray>?=null
    override fun scan(onDevice:(BleDevice)->Unit){
        stopScan()
        callback=object:ScanCallback(){ override fun onScanResult(type:Int,r:ScanResult){ onDevice(BleDevice(r.device.address,r.device.name,r.rssi)) } }
        adapter.bluetoothLeScanner?.startScan(callback)
    }
    override fun stopScan(){ callback?.let{adapter.bluetoothLeScanner?.stopScan(it)}; callback=null }
    override fun connect(deviceId:String){ gatt?.close(); gatt=adapter.getRemoteDevice(deviceId).connectGatt(appContext,false,object:BluetoothGattCallback(){
        override fun onConnectionStateChange(g:BluetoothGatt,s:Int,n:Int){ if(n==BluetoothProfile.STATE_CONNECTED) g.discoverServices() }
        override fun onServicesDiscovered(g:BluetoothGatt,s:Int){ pending?.let{writeNow(g,it.first,it.second,it.third)} }
    }) }
    override fun disconnect(){ gatt?.disconnect(); gatt?.close(); gatt=null }
    override fun write(serviceUuid:String, characteristicUuid:String, bytes:ByteArray){
        val x=Triple(UUID.fromString(serviceUuid),UUID.fromString(characteristicUuid),bytes); pending=x; gatt?.let{writeNow(it,x.first,x.second,x.third)}
    }
    private fun writeNow(g:BluetoothGatt,s:UUID,c:UUID,b:ByteArray){ val ch=g.getService(s)?.getCharacteristic(c)?:return; g.writeCharacteristic(ch,b,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) }
}
