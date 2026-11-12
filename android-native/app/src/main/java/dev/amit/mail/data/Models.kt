package dev.amit.mail.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class Folder(val key: String, val title: String) {
    INBOX("inbox", "Inbox"),
    SENT("sent", "Sent"),
    DRAFTS("drafts", "Drafts"),
    TRASH("trash", "Bin");

    companion object {
        fun of(key: String?): Folder = entries.firstOrNull { it.key == key } ?: INBOX
    }
}

data class Attachment(
    val filename: String,
    val contentType: String?,
    val size: Long,
    val fid: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("filename", filename)
        .put("contentType", contentType)
        .put("size", size)
        .put("fid", fid)
}

data class Mail(
    val id: String,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val origin: String?,
    val subject: String,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val attachmentCount: Int,
    val html: String? = null,
    val text: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val templateHtml: String? = null,
    val templateName: String? = null,
    val htmlMode: Boolean = false,
    val replyToId: String? = null,
) {
    val hasBody: Boolean get() = html != null || text != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("from", from)
        .put("to", JSONArray(to)).put("cc", JSONArray(cc)).put("bcc", JSONArray(bcc))
        .put("origin", origin).put("subject", subject).put("snippet", snippet)
        .put("date", date).put("read", read).put("attachmentCount", attachmentCount)
        .put("html", html).put("text", text)
        .put("attachments", JSONArray().also { a -> attachments.forEach { a.put(it.toJson()) } })
        .put("templateHtml", templateHtml).put("templateName", templateName)
        .put("htmlMode", htmlMode).put("replyToId", replyToId)
}

data class Template(val id: String, val name: String, val html: String, val isDefault: Boolean)

data class Config(val aliases: List<String>, val fromName: String)

data class MailPage(val mails: List<Mail>, val hasMore: Boolean, val unread: Int?)

// ---- JSON helpers (org.json returns "null" for JSON null in optString, so go through isNull) ----

fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

fun JSONObject.toAttachment() = Attachment(
    filename = str("filename") ?: "attachment",
    contentType = str("contentType"),
    size = optLong("size"),
    fid = str("fid"),
)

fun JSONObject.toMail(): Mail {
    val d = opt("date")
    val ms = when (d) {
        is Number -> d.toLong()
        is String -> runCatching { Instant.parse(d).toEpochMilli() }.getOrDefault(0L)
        else -> 0L
    }
    val atts = optJSONArray("attachments")
    val list = if (atts == null) emptyList() else (0 until atts.length()).mapNotNull { atts.optJSONObject(it)?.toAttachment() }
    return Mail(
        id = optString("id"),
        from = optString("from"),
        to = optJSONArray("to").strings(),
        cc = optJSONArray("cc").strings(),
        bcc = optJSONArray("bcc").strings(),
        origin = str("origin"),
        subject = str("subject") ?: "(no subject)",
        snippet = optString("snippet"),
        date = ms,
        read = optBoolean("read", true),
        attachmentCount = if (has("attachmentCount")) optInt("attachmentCount") else list.size,
        html = str("html"),
        text = str("text"),
        attachments = list,
        templateHtml = str("templateHtml"),
        templateName = str("templateName"),
        htmlMode = optBoolean("htmlMode", false),
        replyToId = str("replyToId"),
    )
}
