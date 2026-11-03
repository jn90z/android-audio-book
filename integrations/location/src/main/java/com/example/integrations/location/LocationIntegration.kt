package com.example.integrations.location

data class AppLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)
interface LocationIntegration {
    fun current(onResult: (Result<AppLocation>) -> Unit)
    fun observe(onLocation: (AppLocation) -> Unit): AutoCloseable
}
