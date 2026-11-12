package dev.amit.mail.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Last-seen mail kept on the device, so lists and opened messages show without the network. */
class Cache(ctx: Context) {
    private val dir = File(ctx.filesDir, "cache").apply { mkdirs() }

    @Synchronized
    fun saveList(folder: Folder, list: List<Mail>) {
        runCatching {
            val a = JSONArray()
            list.forEach { a.put(it.toJson()) }
            File(dir, "list_${folder.key}.json").writeText(a.toString())
        }
    }

    @Synchronized
    fun loadList(folder: Folder): List<Mail> = runCatching {
        val a = JSONArray(File(dir, "list_${folder.key}.json").readText())
        (0 until a.length()).map { a.getJSONObject(it).toMail() }
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveMail(folder: Folder, m: Mail) {
        runCatching {
            File(dir, "mail_${folder.key}_${m.id}.json").writeText(m.toJson().toString())
            dir.listFiles { f -> f.name.startsWith("mail_") }
                ?.sortedByDescending { it.lastModified() }?.drop(80)?.forEach { it.delete() }
        }
    }

    @Synchronized
    fun loadMail(folder: Folder, id: String): Mail? = runCatching {
        org.json.JSONObject(File(dir, "mail_${folder.key}_$id.json").readText()).toMail()
    }.getOrNull()

    @Synchronized
    fun clear() {
        dir.deleteRecursively()
        dir.mkdirs()
    }
}
