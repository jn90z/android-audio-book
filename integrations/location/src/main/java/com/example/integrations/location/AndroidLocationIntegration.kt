package com.example.integrations.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal

@SuppressLint("MissingPermission")
class AndroidLocationIntegration(private val appContext:Context):LocationIntegration {
    private val lm=appContext.getSystemService(LocationManager::class.java)
    override fun current(onResult:(Result<AppLocation>)->Unit){
        val provider=if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        lm.getCurrentLocation(provider,CancellationSignal(),appContext.mainExecutor){ l ->
            if(l==null) onResult(Result.failure(IllegalStateException("Location unavailable")))
            else onResult(Result.success(AppLocation(l.latitude,l.longitude,l.accuracy)))
        }
    }
    override fun observe(onLocation:(AppLocation)->Unit):AutoCloseable {
        val listener=android.location.LocationListener{l->onLocation(AppLocation(l.latitude,l.longitude,l.accuracy))}
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,listener)
        return AutoCloseable{lm.removeUpdates(listener)}
    }
}
