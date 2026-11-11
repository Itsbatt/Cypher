package com.cypher.music.sync

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cypher.music.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Real Google sign-in through Credential Manager.
 *
 * Cypher never sees a password and never keeps refresh tokens: Google returns an ID
 * token, which is stored encrypted and only used as a bearer credential for the sync
 * backend. When the web client id is missing from the build the gateway reports
 * [AuthResult.NotConfigured] instead of showing a button that cannot work.
 */
class GoogleAuthGateway(private val context: Context) {

    private val clientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val isConfigured: Boolean get() = clientId.isNotBlank()

    suspend fun signIn(activityContext: Context): AuthResult {
        if (!isConfigured) return AuthResult.NotConfigured
        val credentialManager = CredentialManager.create(activityContext)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleCredential.idToken
                val email = JwtClaims.email(token).orEmpty()
                val account = CloudAccount(
                    userId = JwtClaims.subject(token) ?: googleCredential.id,
                    email = email,
                    displayName = googleCredential.displayName
                        ?: email.substringBefore('@').ifBlank { "Cuenta de Google" },
                    photoUrl = googleCredential.profilePictureUri?.toString()
                )
                if (account.userId.isBlank()) {
                    AuthResult.Failed("Google no devolvió un identificador de cuenta válido.")
                } else {
                    AuthResult.Success(account, token)
                }
            } else {
                AuthResult.Failed("El selector devolvió un tipo de credencial no admitido.")
            }
        } catch (cancel: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (noCredential: NoCredentialException) {
            Log.w(TAG, "No credential: ${noCredential.type} ${noCredential.message}")
            AuthResult.Failed(
                "Google no ofreció ninguna cuenta utilizable. Comprueba que la app está " +
                    "registrada en Google Cloud (paquete ${context.packageName} y huella " +
                    "SHA-1 de la firma)."
            )
        } catch (io: IOException) {
            Log.w(TAG, "Sign-in network error: ${io.message}")
            AuthResult.NoNetwork
        } catch (error: GetCredentialException) {
            Log.w(TAG, "Sign-in failed: ${error.type} ${error.message}")
            AuthResult.Failed(error.message ?: "Google rechazó el inicio de sesión.")
        } catch (error: Exception) {
            Log.e(TAG, "Unexpected sign-in error", error)
            AuthResult.Failed(error.message ?: "Error inesperado al iniciar sesión.")
        }
    }

    /** Forgets the credential state so the next sign-in asks again. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w(TAG, "clearCredentialState: ${it.message}") }
        Unit
    }

    private companion object {
        const val TAG = "GoogleAuthGateway"
    }
}
