package dev.amit.mail.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.amit.mail.mailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notification buttons: Mark read / Delete one mail on the server with its push token (no sign-in needed). */
class MailActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val op = i.getStringExtra("op") ?: return
        val id = i.getStringExtra("id") ?: return
        val t = i.getStringExtra("t") ?: return
        NotificationManagerCompat.from(ctx).cancel(i.getIntExtra("nid", 0))
        val pending = goAsync()
        val api = ctx.mailApp.api
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                api.pushAction(op, id, t)
            } catch (ignored: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
