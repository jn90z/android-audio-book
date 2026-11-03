package com.example.platform.diagnostics

interface AppLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

object NoOpLogger : AppLogger {
    override fun debug(message:String) {}
    override fun info(message:String) {}
    override fun warn(message:String,error:Throwable?) {}
    override fun error(message:String,error:Throwable?) {}
}
