package dev.amit.mail.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.amit.mail.MailApp
import dev.amit.mail.MainActivity
import dev.amit.mail.R
import dev.amit.mail.mailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the server's data-only FCM message and builds the notification itself (Mark read / Delete / Reply). */
class MailMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = mailApp
        app.prefs.pendingFcm = token
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (app.auth.signedIn) app.registerPush()
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        if (MainActivity.visible) { // the app is on screen: refresh the list instead of notifying
            MailBus.newMail.tryEmit(Unit)
            return
        }
        val d = msg.data
        val id = d["id"]
        val t = d["t"]
        val title = d["title"] ?: "New mail"
        val body = d["body"] ?: ""
        val nid = (if (id != null) id.hashCode() and 0x7fffffff else (System.currentTimeMillis() and 0x7fffffff).toInt()) or 2

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val b = NotificationCompat.Builder(this, MailApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setColor(0xFF4FD1B5.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
            .setContentIntent(PendingIntent.getActivity(this, nid, open(id, false), flags))
        if (id != null && t != null) {
            b.addAction(0, "Mark read", PendingIntent.getBroadcast(this, nid + 1, act("read", id, t, nid), flags))
            b.addAction(0, "Delete", PendingIntent.getBroadcast(this, nid + 3, act("delete", id, t, nid), flags))
            b.addAction(0, "Reply", PendingIntent.getActivity(this, nid + 5, open(id, true), flags))
        }
        val nmc = NotificationManagerCompat.from(this)
        runCatching { nmc.notify(nid, b.build()) }

        val nm = getSystemService(NotificationManager::class.java)
        val active = nm.activeNotifications.count { it.id != SUMMARY_ID }
        if (active >= 2) {
            val s = NotificationCompat.Builder(this, MailApp.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setColor(0xFF4FD1B5.toInt())
                .setContentTitle("$active new mails")
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("$active new mails"))
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 1, open(null, false), flags))
            runCatching { nmc.notify(SUMMARY_ID, s.build()) }
        }
    }

    private fun open(id: String?, reply: Boolean) =
        Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .also { if (id != null) it.putExtra("mailId", id).putExtra("reply", reply) }

    private fun act(op: String, id: String, t: String, nid: Int) =
        Intent(this, MailActionReceiver::class.java).putExtra("op", op).putExtra("id", id).putExtra("t", t).putExtra("nid", nid)

    companion object {
        const val GROUP = "mail"
        const val SUMMARY_ID = 1
    }
}
