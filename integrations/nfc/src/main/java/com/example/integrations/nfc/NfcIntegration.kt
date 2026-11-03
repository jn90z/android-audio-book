package com.example.integrations.nfc

data class NfcPayload(val bytes: ByteArray)
interface NfcIntegration {
    fun onPayload(listener: (NfcPayload) -> Unit)
    fun write(payload: NfcPayload)
}
