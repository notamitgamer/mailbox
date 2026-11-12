package dev.amit.mail.data

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dev.amit.mail.R
import kotlinx.coroutines.tasks.await

/** Google sign-in through Credential Manager, exchanged for a Firebase session. The server wants its ID token. */
class AuthManager {
    private val auth get() = FirebaseAuth.getInstance()

    val email: String? get() = auth.currentUser?.email
    val signedIn: Boolean get() = auth.currentUser != null

    suspend fun idToken(force: Boolean = false): String? =
        auth.currentUser?.getIdToken(force)?.await()?.token

    suspend fun signIn(activity: Activity) {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        } else {
            error("Google sign-in returned an unexpected credential")
        }
    }

    suspend fun signOut(activity: Activity?) {
        auth.signOut()
        if (activity != null) runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
