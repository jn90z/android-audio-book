package com.example.integrations.iot

enum class Capability { DIGITAL_OUTPUT, PWM, RGB, RGBW, SENSOR, RELAY, MOTOR, CUSTOM }
data class Device(
    val id: String,
    val name: String,
    val online: Boolean,
    val capabilities: Set<Capability>,
    val telemetry: Map<String,String> = emptyMap()
)
data class DeviceCommand(val deviceId:String, val capability:Capability, val action:String, val value:String?=null)

interface DeviceTransport {
    fun discover(onDevice:(Device)->Unit)
    fun send(command:DeviceCommand)
    fun observe(deviceId:String, onDevice:(Device)->Unit):AutoCloseable
}
