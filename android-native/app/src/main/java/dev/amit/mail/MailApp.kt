package dev.amit.mail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import dev.amit.mail.data.Api
import dev.amit.mail.data.AuthManager
import dev.amit.mail.data.Cache
import dev.amit.mail.data.Prefs
import kotlinx.coroutines.tasks.await

class MailApp : Application() {
    lateinit var prefs: Prefs
    lateinit var cache: Cache
    val auth = AuthManager()
    lateinit var api: Api

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        cache = Cache(this)
        api = Api { force -> auth.idToken(force) }
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "New mail", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }

    /** Gives the server this phone's FCM token (new mail pushes are sent to every saved token). */
    suspend fun registerPush() {
        val token = runCatching { prefs.pendingFcm ?: FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        runCatching {
            api.registerFcm(token)
            prefs.pendingFcm = null
        }.onFailure { prefs.pendingFcm = token }
    }

    companion object {
        const val CHANNEL = "mail"
    }
}

val Context.mailApp: MailApp get() = applicationContext as MailApp
