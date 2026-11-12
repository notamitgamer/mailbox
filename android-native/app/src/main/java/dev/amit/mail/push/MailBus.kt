package dev.amit.mail.push

import kotlinx.coroutines.flow.MutableSharedFlow

/** The messaging service tells the open app that new mail arrived, so it refreshes instead of notifying. */
object MailBus {
    val newMail = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
}
