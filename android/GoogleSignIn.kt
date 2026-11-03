package com.example.app.auth

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleCredential(val idToken: String, val nonce: String)

class GoogleSignIn(
    private val activity: Activity,
    private val webClientId: String
) {
    private val credentials = CredentialManager.create(activity)

    suspend fun signIn(): GoogleCredential {
        require(webClientId.isNotBlank())
        val nonce = randomNonce()
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(nonce)
            .build()
        val response = credentials.getCredential(
            activity,
            GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val raw = response.credential
        check(raw is CustomCredential &&
              raw.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val google = GoogleIdTokenCredential.createFrom(raw.data)
        return GoogleCredential(google.idToken, nonce)
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
