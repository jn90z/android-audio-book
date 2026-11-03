package com.example.integrations.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.NdefMessage
import android.nfc.NdefRecord

class AndroidNfcIntegration(private val activity:Activity):NfcIntegration,NfcAdapter.ReaderCallback {
    private val adapter=NfcAdapter.getDefaultAdapter(activity)
    private var listener:(NfcPayload)->Unit={}
    private var pending:NfcPayload?=null
    override fun onPayload(listener:(NfcPayload)->Unit){this.listener=listener}
    fun enable(){adapter?.enableReaderMode(activity,this,NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_V,null)}
    fun disable(){adapter?.disableReaderMode(activity)}
    override fun write(payload:NfcPayload){pending=payload}
    override fun onTagDiscovered(tag:Tag){
        val ndef=Ndef.get(tag)?:return
        runCatching{ndef.connect(); pending?.let{ndef.writeNdefMessage(NdefMessage(arrayOf(NdefRecord.createMime("application/octet-stream",it.bytes))));pending=null}
            ?: ndef.ndefMessage?.records?.firstOrNull()?.payload?.let{listener(NfcPayload(it))}; ndef.close()}
    }
}
