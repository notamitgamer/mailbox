package dev.amit.mail.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.MailTo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.FileProvider
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.amit.mail.MailApp
import dev.amit.mail.data.Api
import dev.amit.mail.data.ApiException
import dev.amit.mail.data.Attachment
import dev.amit.mail.data.Folder
import dev.amit.mail.data.Mail
import dev.amit.mail.data.Template
import dev.amit.mail.data.escHtml
import dev.amit.mail.data.fullDate
import dev.amit.mail.data.parseAddr
import dev.amit.mail.data.plainOf
import dev.amit.mail.push.MailBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed interface Route {
    data class Reader(val id: String, val folder: Folder) : Route
    data class Compose(val key: Long) : Route
    data object Settings : Route
}

/** A message for the snackbar, optionally with one action (Undo). */
data class UiEvent(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class ComposeArgs(
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val from: String = "",
    val subject: String = "",
    val body: String = "",
    val replyToId: String? = null,
    val draftId: String? = null,
    val htmlMode: Boolean = false,
    val templateHtml: String = "",
    val templateName: String = "",
    val files: List<Attachment> = emptyList(),
    val title: String = "New message",
)

class ComposeFile(val name: String, val type: String, size: Long) {
    var size by mutableStateOf(size)
    var fid by mutableStateOf<String?>(null)
    var uploading by mutableStateOf(true)
}

private val SPLIT = Regex("[,;\\s]+")
private val EMAIL = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$")
fun splitAddrs(s: String): List<String> = s.split(SPLIT).map { it.trim().trim('<', '>') }.filter { it.isNotEmpty() }
fun validAddr(s: String) = EMAIL.matches(s)

class ComposeState(a: ComposeArgs) {
    var from by mutableStateOf(a.from)
    var to by mutableStateOf(a.to)
    var cc by mutableStateOf(a.cc)
    var bcc by mutableStateOf(a.bcc)
    var toInput by mutableStateOf("")
    var ccInput by mutableStateOf("")
    var bccInput by mutableStateOf("")
    var subject by mutableStateOf(a.subject)
    var bodyField by mutableStateOf(TextFieldValue(a.body, TextRange(0))) // the cursor starts above a reply's quote
    var htmlMode by mutableStateOf(a.htmlMode)
    var templateHtml by mutableStateOf(a.templateHtml)
    var templateName by mutableStateOf(a.templateName)
    var templateTouched = false
    val replyToId: String? = a.replyToId
    var draftId by mutableStateOf(a.draftId)
    val files = mutableStateListOf<ComposeFile>().also { l ->
        a.files.forEach { x -> l.add(ComposeFile(x.filename, x.contentType ?: "", x.size).also { it.fid = x.fid; it.uploading = false }) }
    }
    var showCc by mutableStateOf(a.cc.isNotEmpty() || a.bcc.isNotEmpty())
    var status by mutableStateOf("")
    var sending by mutableStateOf(false)
    var edits by mutableIntStateOf(0)
    var dirty = false
    val title = a.title

    val body: String get() = bodyField.text

    fun markDirty() {
        dirty = true
        edits++
    }

    /** Turns text still sitting in an address field into addresses. */
    fun commitInputs() {
        if (toInput.isNotBlank()) { to = (to + splitAddrs(toInput)).distinct(); toInput = "" }
        if (ccInput.isNotBlank()) { cc = (cc + splitAddrs(ccInput)).distinct(); ccInput = "" }
        if (bccInput.isNotBlank()) { bcc = (bcc + splitAddrs(bccInput)).distinct(); bccInput = "" }
    }

    fun isEmpty() = to.isEmpty() && cc.isEmpty() && bcc.isEmpty() && toInput.isBlank() && ccInput.isBlank() && bccInput.isBlank() &&
        subject.isBlank() && body.isBlank() && templateHtml.isEmpty() && files.isEmpty()

    fun draftJson(): JSONObject = JSONObject()
        .put("id", draftId)
        .put("from", from)
        .put("to", JSONArray(to)).put("cc", JSONArray(cc)).put("bcc", JSONArray(bcc))
        .put("subject", subject).put("text", body).put("htmlMode", htmlMode)
        .put("replyToId", replyToId)
        .put("templateHtml", templateHtml).put("templateName", templateName)
        .put("attachments", JSONArray().also { a -> files.forEach { f -> f.fid?.let { a.put(JSONObject().put("fid", it)) } } })
}

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val ma = application as MailApp
    private val api: Api = ma.api
    private val cache = ma.cache
    private val prefs = ma.prefs
    private val auth = ma.auth

    // ---- account and settings ----
    var signedIn by mutableStateOf(auth.signedIn)
    var email by mutableStateOf(auth.email ?: "")
    var signInBusy by mutableStateOf(false)
    var signInError by mutableStateOf<String?>(null)
    var theme by mutableStateOf(prefs.theme)
    var dynamicColor by mutableStateOf(prefs.dynamicColor)
    var aliases by mutableStateOf(prefs.aliases)

    // ---- list ----
    var folder by mutableStateOf(Folder.INBOX)
    var mails by mutableStateOf<List<Mail>>(emptyList())
    var refreshing by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var unread by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var unreadOnly by mutableStateOf(false)
    var selection by mutableStateOf<Set<String>>(emptySet())
    var draftCount by mutableIntStateOf(0)
    var recentSearches by mutableStateOf(prefs.recentSearches)

    /** Held here so the list keeps its scroll position while a message or the composer is open on top of it. */
    val listState = LazyListState()

    /** On wide screens the message opens beside the list instead of on top of it. */
    var twoPane by mutableStateOf(false)

    // ---- screens on top of the list ----
    val stack = mutableStateListOf<Route>()
    var reader by mutableStateOf<Mail?>(null)
    var readerLoading by mutableStateOf(false)
    var compose by mutableStateOf<ComposeState?>(null)
    var templates by mutableStateOf<List<Template>>(emptyList())

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)

    private var lastRefresh = 0L
    private var composeSeq = 0L
    private val draftLock = Mutex()
    private var pendingOpen: Pair<String, Boolean>? = null
    private var pendingCompose: ComposeArgs? = null

    init {
        viewModelScope.launch {
            MailBus.newMail.collect { if (signedIn && folder == Folder.INBOX) refresh() }
        }
        if (signedIn) bootstrap()
    }

    private fun toast(msg: String) {
        events.tryEmit(UiEvent(msg))
    }

    private fun handleError(e: ApiException, quiet: Boolean = false) {
        when (e.status) {
            401, 403 -> {
                toast(if (e.status == 403) "This Google account is not allowed. Sign in with the mailbox owner's account." else "Your sign-in expired. Sign in again.")
                signOut(null)
            }
            else -> if (!quiet) toast(e.message ?: "Something went wrong")
        }
    }

    // ---- sign-in ----

    fun signIn(activity: Activity) {
        if (signInBusy) return
        viewModelScope.launch {
            signInBusy = true
            signInError = null
            try {
                auth.signIn(activity)
                signedIn = true
                email = auth.email ?: ""
                bootstrap()
                processPending()
            } catch (ignored: GetCredentialCancellationException) {
                // the person closed the account picker
            } catch (e: Exception) {
                signInError = e.message ?: "Sign-in failed"
            } finally {
                signInBusy = false
            }
        }
    }

    fun signOut(activity: Activity?) {
        viewModelScope.launch {
            auth.signOut(activity)
            cache.clear()
            signedIn = false
            email = ""
            mails = emptyList()
            stack.clear()
            reader = null
            compose = null
            selection = emptySet()
            folder = Folder.INBOX
        }
    }

    private fun bootstrap() {
        email = auth.email ?: ""
        viewModelScope.launch {
            runCatching { api.config() }.onSuccess {
                aliases = it.aliases
                prefs.aliases = it.aliases
            }
        }
        viewModelScope.launch { ma.registerPush() }
        mails = cache.loadList(folder)
        refresh()
        refreshCounts()
    }

    fun onResume() {
        if (!signedIn) return
        if (System.currentTimeMillis() - lastRefresh > 30_000) refresh()
        if (prefs.pendingFcm != null) viewModelScope.launch { ma.registerPush() }
    }

    fun updateTheme(v: String) { theme = v; prefs.theme = v }
    fun setDynamic(v: Boolean) { dynamicColor = v; prefs.dynamicColor = v }

    fun testPush() {
        viewModelScope.launch {
            try { api.testPush(); toast("Test notification sent") } catch (e: ApiException) { handleError(e) }
        }
    }

    // ---- list ----

    fun selectFolder(f: Folder) {
        if (f == folder) return
        folder = f
        query = ""
        unreadOnly = false
        selection = emptySet()
        mails = cache.loadList(f)
        hasMore = false
        refresh()
    }

    fun refresh() {
        if (!signedIn) return
        val f = folder
        viewModelScope.launch {
            refreshing = true
            try {
                val p = api.list(f)
                if (f != folder) return@launch
                mails = p.mails
                hasMore = p.hasMore
                if (f == Folder.INBOX && p.unread != null) unread = p.unread
                if (f == Folder.DRAFTS) draftCount = p.mails.size
                cache.saveList(f, p.mails)
                lastRefresh = System.currentTimeMillis()
                if (f != Folder.DRAFTS) refreshCounts()
            } catch (e: ApiException) {
                handleError(e, quiet = e.status == 0 && mails.isNotEmpty())
            } finally {
                refreshing = false
            }
        }
    }

    /** Drafts shown as a badge on the folder icon. */
    fun refreshCounts() {
        viewModelScope.launch {
            runCatching { api.list(Folder.DRAFTS, limit = 100) }.onSuccess { draftCount = it.mails.size }
        }
    }

    fun commitSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        recentSearches = (listOf(t) + recentSearches.filterNot { it.equals(t, ignoreCase = true) }).take(8)
        prefs.recentSearches = recentSearches
    }

    fun removeRecent(q: String) {
        recentSearches = recentSearches - q
        prefs.recentSearches = recentSearches
    }

    fun clearRecent() {
        recentSearches = emptyList()
        prefs.recentSearches = emptyList()
    }

    fun loadMore() {
        val last = mails.lastOrNull() ?: return
        if (!hasMore || loadingMore || refreshing) return
        val f = folder
        loadingMore = true
        viewModelScope.launch {
            try {
                val p = api.list(f, before = last.date)
                if (f == folder) {
                    val known = mails.map { it.id }.toSet()
                    mails = mails + p.mails.filter { it.id !in known }
                    hasMore = p.hasMore
                }
            } catch (e: ApiException) {
                handleError(e)
            } finally {
                loadingMore = false
            }
        }
    }

    fun toggleSelect(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun selectAll(ids: List<String>) {
        selection = ids.toSet()
    }

    private fun setReadLocal(ids: Set<String>, read: Boolean) {
        var delta = 0
        mails = mails.map {
            if (it.id in ids && it.read != read) {
                delta += if (read) -1 else 1
                it.copy(read = read)
            } else it
        }
        if (folder == Folder.INBOX) unread = maxOf(0, unread + delta)
        reader?.let { if (it.id in ids) reader = it.copy(read = read) }
    }

    fun setRead(ids: Set<String>, read: Boolean) {
        if (folder != Folder.INBOX) return
        setReadLocal(ids, read)
        selection = emptySet()
        viewModelScope.launch {
            try { ids.forEach { api.markRead(it, read) } } catch (e: ApiException) { handleError(e); refresh() }
        }
    }

    // ---- opening, deleting, restoring ----

    fun openMail(m: Mail) {
        if (folder == Folder.DRAFTS) { editDraft(m); return }
        val f = folder
        if (twoPane) stack.removeAll { it is Route.Reader }
        stack.add(Route.Reader(m.id, f))
        reader = cache.loadMail(f, m.id) ?: m
        loadReader(m.id, f)
    }

    private fun loadReader(id: String, f: Folder) {
        viewModelScope.launch {
            readerLoading = true
            try {
                val full = api.get(id, f)
                cache.saveMail(f, full)
                if (reader?.id == id) reader = full
                if (f == Folder.INBOX && !full.read) {
                    setReadLocal(setOf(id), true)
                    runCatching { api.markRead(id, true) }
                }
            } catch (e: ApiException) {
                if (e.status == 404) { toast("That message no longer exists"); back(); refresh() } else handleError(e, quiet = reader?.hasBody == true)
            } finally {
                readerLoading = false
            }
        }
    }

    /** Inbox and sent mail go to the bin (with Undo); drafts and bin mail are deleted for good. */
    fun deleteMails(ids: Set<String>, from: Folder = folder) {
        if (ids.isEmpty()) return
        if (from == folder) {
            val gone = mails.filter { it.id in ids }
            mails = mails.filterNot { it.id in ids }
            if (from == Folder.INBOX) unread = maxOf(0, unread - gone.count { !it.read })
        }
        selection = emptySet()
        viewModelScope.launch {
            var failed = 0
            for (id in ids) {
                try { api.delete(id, from) } catch (e: ApiException) {
                    failed++
                    if (e.status == 401 || e.status == 403) { handleError(e); return@launch }
                }
            }
            if (failed > 0) { toast("Couldn't delete $failed of ${ids.size}"); refresh(); return@launch }
            if (from == Folder.INBOX || from == Folder.SENT) {
                events.tryEmit(UiEvent(if (ids.size == 1) "Moved to Bin" else "${ids.size} moved to Bin", "Undo") { undoDelete(ids) })
            } else {
                toast(if (ids.size == 1) "Deleted" else "${ids.size} deleted")
            }
        }
    }

    private fun undoDelete(ids: Set<String>) {
        viewModelScope.launch {
            ids.forEach { runCatching { api.restore(it) } }
            refresh()
        }
    }

    fun restoreMails(ids: Set<String>) {
        mails = mails.filterNot { it.id in ids }
        selection = emptySet()
        viewModelScope.launch {
            try { ids.forEach { api.restore(it) }; toast(if (ids.size == 1) "Restored" else "${ids.size} restored") } catch (e: ApiException) { handleError(e); refresh() }
        }
    }

    fun emptyBin() {
        mails = emptyList()
        viewModelScope.launch {
            try { api.emptyTrash(); toast("Bin emptied") } catch (e: ApiException) { handleError(e); refresh() }
        }
    }

    fun deleteOpen() {
        val m = reader ?: return
        val f = (stack.lastOrNull() as? Route.Reader)?.folder ?: folder
        back()
        deleteMails(setOf(m.id), f)
    }

    fun restoreOpen() {
        val m = reader ?: return
        back()
        restoreMails(setOf(m.id))
    }

    fun markUnreadOpen() {
        val m = reader ?: return
        setReadLocal(setOf(m.id), false)
        back()
        viewModelScope.launch { try { api.markRead(m.id, false) } catch (e: ApiException) { handleError(e) } }
    }

    // ---- reply, forward ----

    fun replyTo(all: Boolean) {
        val m = reader ?: return
        val mine = aliases.map { it.lowercase() }.toSet()
        val p = parseAddr(m.from)
        val seen = mutableSetOf(p.addr.lowercase())
        fun others(l: List<String>) = l.map { parseAddr(it).addr }.filter { it.isNotBlank() && it.lowercase() !in mine && seen.add(it.lowercase()) }
        val to = if (all) listOf(p.addr) + others(m.to) else listOf(p.addr)
        val cc = if (all) others(m.cc) else emptyList()
        val quote = plainOf(m).take(2000).lines().joinToString("\n") { "> $it" }
        val subject = if (Regex("^re:", RegexOption.IGNORE_CASE).containsMatchIn(m.subject)) m.subject else "Re: ${m.subject}"
        openCompose(
            ComposeArgs(
                to = to, cc = cc, subject = subject, replyToId = m.id,
                body = "\n\nOn ${fullDate(m.date)}, ${p.name.ifBlank { p.addr }} wrote:\n$quote",
                title = if (all) "Reply all" else "Reply",
            ),
        )
    }

    /** Sends the open mail on as it is. */
    fun forwardAsIs(to: List<String>) {
        val m = reader ?: return
        val f = (stack.lastOrNull() as? Route.Reader)?.folder ?: folder
        viewModelScope.launch {
            try {
                val skipped = api.forward(m.id, f, to)
                toast(if (skipped > 0) "Forwarded ($skipped attachment${if (skipped == 1) "" else "s"} could not go along)" else "Forwarded")
            } catch (e: ApiException) { handleError(e) }
        }
    }

    /** Opens a new message with the open mail quoted and its files carried over. */
    fun forwardEdit() {
        val m = reader ?: return
        val f = (stack.lastOrNull() as? Route.Reader)?.folder ?: folder
        val head = listOf(
            "---------- Forwarded message ----------", "From: ${m.from}", "Date: ${fullDate(m.date)}",
            "Subject: ${m.subject}", "To: ${m.to.joinToString(", ")}",
        ).joinToString("\n")
        openCompose(
            ComposeArgs(
                subject = if (Regex("^fwd?:", RegexOption.IGNORE_CASE).containsMatchIn(m.subject)) m.subject else "Fwd: ${m.subject}",
                body = "\n\n$head\n\n${plainOf(m)}", title = "Forward",
            ),
        )
        val c = compose ?: return
        m.attachments.forEachIndexed { n, a ->
            val file = ComposeFile(a.filename, a.contentType ?: "", a.size)
            c.files.add(file)
            viewModelScope.launch {
                try {
                    val copy = api.copyAttachment(m.id, n, f)
                    file.fid = copy.fid
                    file.size = copy.size
                } catch (e: ApiException) {
                    c.files.remove(file)
                    toast("\"${a.filename}\": ${e.message}")
                } finally { file.uploading = false }
                c.markDirty()
            }
        }
    }

    // ---- compose ----

    fun openCompose(a: ComposeArgs) {
        val c = ComposeState(a)
        compose = c
        stack.add(Route.Compose(++composeSeq))
        if (a.templateHtml.isEmpty() && a.draftId == null) {
            viewModelScope.launch {
                val def = loadTemplates().firstOrNull { it.isDefault } ?: return@launch
                if (compose === c && !c.templateTouched && c.templateHtml.isEmpty()) {
                    c.templateHtml = def.html
                    c.templateName = def.name
                }
            }
        }
    }

    suspend fun loadTemplates(force: Boolean = false): List<Template> {
        if (templates.isEmpty() || force) templates = runCatching { api.templates() }.getOrDefault(templates)
        return templates
    }

    fun chooseTemplate(t: Template?) {
        val c = compose ?: return
        c.templateTouched = true
        c.templateHtml = t?.html ?: ""
        c.templateName = t?.name ?: ""
        c.markDirty()
    }

    private fun editDraft(m: Mail) {
        viewModelScope.launch {
            try {
                val d = api.get(m.id, Folder.DRAFTS)
                openCompose(
                    ComposeArgs(
                        to = d.to, cc = d.cc, bcc = d.bcc, from = d.from,
                        subject = if (d.subject == "(no subject)") "" else d.subject, body = d.text ?: "",
                        replyToId = d.replyToId, draftId = d.id, htmlMode = d.htmlMode,
                        templateHtml = d.templateHtml ?: "", templateName = d.templateName ?: "",
                        files = d.attachments, title = "Draft",
                    ),
                )
            } catch (e: ApiException) { handleError(e) }
        }
    }

    fun saveDraft(c: ComposeState? = compose) {
        if (c == null) return
        viewModelScope.launch { saveDraftNow(c) }
    }

    private suspend fun saveDraftNow(c: ComposeState): Boolean = draftLock.withLock {
        if (!c.dirty || c.isEmpty() || c.sending || c.files.any { it.uploading }) return@withLock false
        c.dirty = false
        try {
            val isNew = c.draftId == null
            c.draftId = api.saveDraft(c.draftJson())
            c.status = "Draft saved"
            if (isNew) draftCount++
            true
        } catch (e: ApiException) {
            if (e.status == 404) c.draftId = null // discarded or sent elsewhere: the next save starts a new draft
            c.dirty = true
            c.status = "Not saved"
            false
        }
    }

    fun attach(uris: List<Uri>) {
        val c = compose ?: return
        val cr = getApplication<Application>().contentResolver
        uris.forEach { uri ->
            var name = "file"
            var size = 0L
            runCatching {
                cr.query(uri, null, null, null, null)?.use { q ->
                    if (q.moveToFirst()) {
                        val ni = q.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = q.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) name = q.getString(ni) ?: name
                        if (si >= 0 && !q.isNull(si)) size = q.getLong(si)
                    }
                }
            }
            val type = cr.getType(uri) ?: "application/octet-stream"
            if (size > 15L * 1024 * 1024) { toast("\"$name\" is over 15 MB"); return@forEach }
            if (c.files.size >= 10) { toast("At most 10 files per message"); return@forEach }
            val file = ComposeFile(name, type, size)
            c.files.add(file)
            viewModelScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                    val a = api.upload(bytes, name, type)
                    file.fid = a.fid
                    file.size = a.size
                    c.markDirty()
                } catch (e: ApiException) {
                    c.files.remove(file)
                    toast("\"$name\": ${e.message}")
                } catch (e: Exception) {
                    c.files.remove(file)
                    toast("Couldn't read \"$name\"")
                } finally { file.uploading = false }
            }
        }
    }

    fun removeFile(f: ComposeFile) {
        val c = compose ?: return
        c.files.remove(f)
        f.fid?.let { fid -> viewModelScope.launch { runCatching { api.deleteUpload(fid) } } }
        c.markDirty()
    }

    fun send() {
        val c = compose ?: return
        if (c.sending) return
        c.commitInputs()
        val bad = (c.to + c.cc + c.bcc).firstOrNull { !validAddr(it) }
        if (bad != null) { toast("\"$bad\" is not a valid address"); return }
        if (c.to.isEmpty()) { toast("Add at least one recipient"); return }
        if (c.files.any { it.uploading }) { toast("Wait for the files to finish uploading"); return }
        val domain = aliases.firstOrNull()?.substringAfter('@', "").orEmpty()
        var from = c.from.trim().lowercase()
        if (from.isNotEmpty() && !from.contains('@') && domain.isNotEmpty()) from = from.trimEnd('@') + "@" + domain
        c.from = from
        c.sending = true
        viewModelScope.launch {
            draftLock.withLock {
                try {
                    val p = JSONObject()
                        .put("to", JSONArray(c.to)).put("cc", JSONArray(c.cc)).put("bcc", JSONArray(c.bcc))
                        .put("subject", c.subject).put("replyToId", c.replyToId).put("draftId", c.draftId)
                        .put("attachments", JSONArray().also { a -> c.files.forEach { f -> f.fid?.let { a.put(JSONObject().put("fid", it)) } } })
                    if (from.isNotEmpty()) p.put("from", from)
                    if (c.templateHtml.isNotEmpty()) { // message first, then the template block
                        val head = if (c.htmlMode) c.body else "<div style=\"white-space:pre-wrap\">${escHtml(c.body)}</div>"
                        p.put("html", "$head<br><br>${c.templateHtml}")
                    } else if (c.htmlMode) p.put("html", c.body) else p.put("text", c.body)
                    api.send(p)
                    c.dirty = false
                    if (c.draftId != null) draftCount = maxOf(0, draftCount - 1)
                    if (compose === c) { compose = null; popTo(Route.Compose::class.java) }
                    toast("Sent")
                    if (folder == Folder.SENT || folder == Folder.DRAFTS) refresh()
                } catch (e: ApiException) {
                    toast(e.message ?: "Send failed")
                    handleError(e, quiet = true)
                } finally { c.sending = false }
            }
        }
    }

    fun discardDraft() {
        val c = compose ?: return
        c.dirty = false
        val id = c.draftId
        compose = null
        popTo(Route.Compose::class.java)
        if (id != null) viewModelScope.launch {
            draftCount = maxOf(0, draftCount - 1)
            runCatching { api.delete(id, Folder.DRAFTS) }
            if (folder == Folder.DRAFTS) refresh()
        }
        c.files.forEach { f -> f.fid?.let { fid -> viewModelScope.launch { runCatching { api.deleteUpload(fid) } } } }
        toast("Draft discarded")
    }

    private fun popTo(type: Class<out Route>) {
        val i = stack.indexOfLast { type.isInstance(it) }
        if (i >= 0) stack.removeAt(i)
    }

    // ---- navigation ----

    fun openSettings() {
        if (stack.lastOrNull() != Route.Settings) stack.add(Route.Settings)
    }

    fun back() {
        when (stack.lastOrNull()) {
            is Route.Compose -> closeCompose()
            is Route.Reader -> { stack.removeAt(stack.lastIndex); if (stack.none { it is Route.Reader }) reader = null }
            Route.Settings -> stack.removeAt(stack.lastIndex)
            null -> {}
        }
    }

    private fun closeCompose() {
        val c = compose
        compose = null
        popTo(Route.Compose::class.java)
        if (c != null) viewModelScope.launch {
            c.commitInputs()
            if (saveDraftNow(c) && folder == Folder.DRAFTS) refresh()
        }
    }

    // ---- files of a mail ----

    fun openAttachment(m: Mail, n: Int, save: Boolean) {
        val a = m.attachments.getOrNull(n) ?: return
        val f = (stack.lastOrNull() as? Route.Reader)?.folder ?: folder
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val name = a.filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dest = File(File(app.cacheDir, "attachments"), "${m.id.take(24)}_${n}_$name")
                if (!dest.exists() || dest.length() == 0L) {
                    toast("Downloading ${a.filename}…")
                    api.download(m.id, n, f, dest)
                    withContext(Dispatchers.IO) { trimFiles() }
                    refreshStorage()
                }
                val mime = a.contentType ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(dest.extension) ?: "application/octet-stream"
                if (save && saveToDownloads(dest, a.filename, mime)) toast("Saved to Downloads") else viewFile(dest, mime)
            } catch (e: ApiException) {
                toast(e.message ?: "Download failed")
            } catch (e: Exception) {
                toast("Couldn't open ${a.filename}")
            }
        }
    }

    // ---- storage used by downloaded files (same idea as the web app's Storage panel) ----

    var fileCount by mutableStateOf(0)
    var fileBytes by mutableStateOf(0L)
    val fileCap = 250L * 1024 * 1024 // past this the oldest downloaded files go first; they download again when opened

    private fun attachmentFiles(): List<File> =
        File(getApplication<Application>().cacheDir, "attachments").listFiles()?.filter { it.isFile }.orEmpty()

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val fs = attachmentFiles()
            fileCount = fs.size
            fileBytes = fs.sumOf { it.length() }
        }
    }

    private fun trimFiles() {
        val fs = attachmentFiles().sortedBy { it.lastModified() }
        var total = fs.sumOf { it.length() }
        if (total <= fileCap) return
        for (f in fs) {
            if (total <= fileCap * 9 / 10) break
            total -= f.length()
            f.delete()
        }
    }

    fun clearFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { attachmentFiles().forEach { it.delete() } }
            refreshStorage()
            toast("Downloaded files cleared")
        }
    }

    private suspend fun saveToDownloads(src: File, name: String, mime: String): Boolean = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return@withContext false
                app.contentResolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
                cv.clear()
                cv.put(MediaStore.Downloads.IS_PENDING, 0)
                app.contentResolver.update(uri, cv, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                src.copyTo(File(dir, name), overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun viewFile(f: File, mime: String) {
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(app, app.packageName + ".files", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { app.startActivity(i) } catch (e: ActivityNotFoundException) { toast("No app can open this file") }
    }

    // ---- intents: notification taps, mailto links, Share to Mailbox ----

    fun handleIntent(i: Intent?) {
        if (i == null) return
        val id = i.getStringExtra("mailId")
        if (id != null) {
            pendingOpen = id to i.getBooleanExtra("reply", false)
            i.removeExtra("mailId")
        } else when (i.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> i.data?.takeIf { it.scheme == "mailto" }?.let { uri ->
                runCatching { MailTo.parse(uri.toString()) }.getOrNull()?.let { mt ->
                    pendingCompose = ComposeArgs(
                        to = splitAddrs(mt.to.orEmpty()), cc = splitAddrs(mt.cc.orEmpty()),
                        subject = mt.subject.orEmpty(), body = mt.body.orEmpty(),
                    )
                }
            }
            Intent.ACTION_SEND -> pendingCompose = ComposeArgs(
                subject = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(), body = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            )
        }
        if (pendingOpen != null || pendingCompose != null) {
            i.action = Intent.ACTION_MAIN
            i.data = null
            processPending()
        }
    }

    private fun processPending() {
        if (!signedIn) return
        pendingOpen?.let { (id, reply) ->
            pendingOpen = null
            openFromNotification(id, reply)
        }
        pendingCompose?.let {
            pendingCompose = null
            openCompose(it)
        }
    }

    private fun openFromNotification(id: String, reply: Boolean) {
        viewModelScope.launch {
            try {
                val full = api.get(id, Folder.INBOX)
                cache.saveMail(Folder.INBOX, full)
                if (folder != Folder.INBOX) selectFolder(Folder.INBOX)
                stack.clear()
                reader = full
                stack.add(Route.Reader(id, Folder.INBOX))
                if (!full.read) {
                    setReadLocal(setOf(id), true)
                    runCatching { api.markRead(id, true) }
                }
                if (reply) replyTo(false)
            } catch (e: ApiException) {
                handleError(e)
            }
        }
    }
}
