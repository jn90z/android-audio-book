package com.example.platform.networking

enum class NetworkState { AVAILABLE, LOST, UNKNOWN }

interface NetworkMonitor {
    fun current(): NetworkState
    fun observe(listener: (NetworkState) -> Unit): AutoCloseable
}
