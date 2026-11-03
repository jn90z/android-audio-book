package com.example.integrations.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

interface CameraIntegration {
    fun startPreview()
    fun capturePhoto(onResult:(Result<ByteArray>)->Unit)
    fun stop()
}

class CameraXCapture(private val capture:ImageCapture, private val executor:Executor):CameraIntegration {
    override fun startPreview() {}
    override fun capturePhoto(onResult:(Result<ByteArray>)->Unit){
        val out=ByteArrayOutputStream()
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(out).build(),executor,object:ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(r:ImageCapture.OutputFileResults)=onResult(Result.success(out.toByteArray()))
            override fun onError(e:ImageCaptureException)=onResult(Result.failure(e))
        })
    }
    override fun stop(){}
}
