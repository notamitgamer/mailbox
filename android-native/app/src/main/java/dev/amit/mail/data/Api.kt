package dev.amit.mail.data

import dev.amit.mail.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

/** The mail server's REST API (server.js). Every call but config and push actions carries the Firebase ID token. */
class Api(private val tokens: suspend (force: Boolean) -> String?) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val base = BuildConfig.BASE_URL
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun exec(method: String, path: String, body: RequestBody?, auth: Boolean = true): Response {
        suspend fun once(force: Boolean): Response = withContext(Dispatchers.IO) {
            val b = Request.Builder().url(base + path)
            if (auth) tokens(force)?.let { b.header("Authorization", "Bearer $it") }
            val rb = body ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody() else null
            b.method(method, rb)
            http.newCall(b.build()).execute()
        }
        try {
            var r = once(false)
            if (r.code == 401 && auth) {
                r.close()
                r = once(true)
            }
            return r
        } catch (e: IOException) {
            throw ApiException(0, "No connection. Check your internet and try again.")
        }
    }

    private suspend fun json(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val r = exec(method, path, body?.toString()?.toRequestBody(jsonType), auth)
        r.use {
            val txt = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            val o = runCatching { JSONObject(txt) }.getOrNull()
            if (!it.isSuccessful) throw ApiException(it.code, o?.str("error") ?: "Request failed (${it.code})")
            return o ?: JSONObject()
        }
    }

    // ---- reading ----

    suspend fun config(): Config {
        val o = json("GET", "/api/config", auth = false)
        return Config(o.optJSONArray("aliases").strings(), o.optString("fromName"))
    }

    suspend fun list(folder: Folder, before: Long? = null, limit: Int = 30): MailPage {
        val q = "/api/mails?folder=${folder.key}&limit=$limit" + if (before != null) "&before=$before" else ""
        val o = json("GET", q)
        val a = o.optJSONArray("mails") ?: JSONArray()
        return MailPage(
            mails = (0 until a.length()).map { a.getJSONObject(it).toMail() },
            hasMore = o.optBoolean("hasMore"),
            unread = if (o.isNull("unread")) null else o.optInt("unread"),
        )
    }

    suspend fun get(id: String, folder: Folder): Mail = json("GET", "/api/mails/${enc(id)}?folder=${folder.key}").toMail()

    suspend fun templates(): List<Template> {
        val a = json("GET", "/api/templates").optJSONArray("templates") ?: JSONArray()
        return (0 until a.length()).map {
            val t = a.getJSONObject(it)
            Template(t.optString("id"), t.optString("name"), t.optString("html"), t.optBoolean("isDefault"))
        }
    }

    // ---- changing mail ----

    suspend fun markRead(id: String, read: Boolean) {
        json("POST", "/api/mails/${enc(id)}/" + if (read) "read" else "unread")
    }

    /** Inbox and sent mail go to the bin; drafts and bin mail are deleted for good. */
    suspend fun delete(id: String, folder: Folder) {
        json("DELETE", "/api/mails/${enc(id)}?folder=${folder.key}")
    }

    suspend fun restore(id: String) {
        json("POST", "/api/mails/${enc(id)}/restore")
    }

    suspend fun emptyTrash() {
        json("DELETE", "/api/trash")
    }

    // ---- writing ----

    /** Saves a draft; returns its id and the files it now holds. */
    suspend fun saveDraft(body: JSONObject): String = json("POST", "/api/drafts", body).optString("id")

    suspend fun send(body: JSONObject) {
        json("POST", "/api/send", body)
    }

    /** Forwards a saved mail as it is. Returns how many attachments could not go along. */
    suspend fun forward(id: String, folder: Folder, to: List<String>): Int {
        val body = JSONObject().put("to", JSONArray(to))
        return json("POST", "/api/mails/${enc(id)}/forward?folder=${folder.key}", body).optInt("skipped")
    }

    // ---- files ----

    suspend fun upload(bytes: ByteArray, name: String, type: String): Attachment {
        val o = exec("POST", "/api/uploads?name=${enc(name)}&type=${enc(type)}", bytes.toRequestBody("application/octet-stream".toMediaType())).use {
            val txt = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            val j = runCatching { JSONObject(txt) }.getOrNull()
            if (!it.isSuccessful) throw ApiException(it.code, j?.str("error") ?: "Upload failed (${it.code})")
            j ?: JSONObject()
        }
        return o.toAttachment()
    }

    suspend fun deleteUpload(fid: String) {
        json("DELETE", "/api/uploads/${enc(fid)}")
    }

    /** Copies an attachment of a saved mail into a new pending upload ("Edit & forward"). */
    suspend fun copyAttachment(id: String, n: Int, folder: Folder): Attachment =
        json("POST", "/api/mails/${enc(id)}/attachments/$n/copy?folder=${folder.key}").toAttachment()

    suspend fun download(id: String, n: Int, folder: Folder, dest: File) {
        exec("GET", "/api/mails/${enc(id)}/attachments/$n?folder=${folder.key}", null).use { r ->
            if (!r.isSuccessful) {
                val txt = withContext(Dispatchers.IO) { r.body?.string().orEmpty() }
                throw ApiException(r.code, runCatching { JSONObject(txt).str("error") }.getOrNull() ?: "Download failed (${r.code})")
            }
            withContext(Dispatchers.IO) {
                dest.parentFile?.mkdirs()
                r.body!!.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    // ---- push ----

    suspend fun registerFcm(token: String) {
        json("POST", "/api/push/fcm", JSONObject().put("token", token))
    }

    suspend fun testPush() {
        json("POST", "/api/push/test")
    }

    /** Notification buttons: no sign-in, the push token is bound to the mail id. */
    suspend fun pushAction(op: String, id: String, token: String) {
        json("POST", "/api/push/action", JSONObject().put("op", op).put("id", id).put("t", token), auth = false)
    }
}
