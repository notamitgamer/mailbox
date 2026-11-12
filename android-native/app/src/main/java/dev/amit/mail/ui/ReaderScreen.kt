@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ReplyAll
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.amit.mail.data.Folder
import dev.amit.mail.data.Mail
import dev.amit.mail.data.escHtml
import dev.amit.mail.data.fmtSize
import dev.amit.mail.data.fullDate
import dev.amit.mail.data.label
import dev.amit.mail.data.parseAddr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: MailViewModel, route: Route.Reader) {
    BackHandler { vm.back() }
    val m = vm.reader
    var menu by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    val folder = route.folder
    val ctx = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton { vm.back() } },
                actions = {
                    if (m != null) {
                        when (folder) {
                            Folder.INBOX -> {
                                IconButton(onClick = { vm.replyTo(false) }) { Icon(Icons.Outlined.Reply, "Reply") }
                                IconButton(onClick = { vm.deleteOpen() }) { Icon(Icons.Outlined.Delete, "Delete") }
                            }
                            Folder.SENT -> IconButton(onClick = { vm.deleteOpen() }) { Icon(Icons.Outlined.Delete, "Delete") }
                            Folder.TRASH -> {
                                IconButton(onClick = { vm.restoreOpen() }) { Icon(Icons.Outlined.RestoreFromTrash, "Restore") }
                                IconButton(onClick = { confirmForever = true }) { Icon(Icons.Outlined.DeleteForever, "Delete forever") }
                            }
                            Folder.DRAFTS -> {}
                        }
                        if (folder != Folder.TRASH) {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (folder == Folder.INBOX) {
                                    DropdownMenuItem(
                                        text = { Text("Reply all") }, leadingIcon = { Icon(Icons.Outlined.ReplyAll, null) },
                                        onClick = { menu = false; vm.replyTo(true) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Forward") }, leadingIcon = { Icon(Icons.Outlined.Forward, null) },
                                    onClick = { menu = false; forward = true },
                                )
                                if (folder == Folder.INBOX) {
                                    DropdownMenuItem(
                                        text = { Text("Mark as unread") }, leadingIcon = { Icon(Icons.Outlined.MarkEmailUnread, null) },
                                        onClick = { menu = false; vm.markUnreadOpen() },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (m == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            if (vm.readerLoading && !m.hasBody) LinearProgressIndicator(Modifier.fillMaxWidth())
            Header(m, folder)
            if (m.attachments.isNotEmpty() && folder != Folder.DRAFTS) Attachments(vm, m)
            HorizontalDivider(Modifier.padding(top = 8.dp))
            if (m.hasBody) {
                MailWebView(m, Modifier.weight(1f).fillMaxWidth(), onLink = { uri ->
                    if (uri.scheme == "mailto") {
                        vm.openCompose(ComposeArgs(to = splitAddrs(uri.schemeSpecificPart.substringBefore('?'))))
                    } else if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "tel") {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                })
            }
        }
    }

    if (forward) ForwardDialog(onDismiss = { forward = false }, onSend = { forward = false; vm.forwardAsIs(it) }, onEdit = { forward = false; vm.forwardEdit() })
    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            title = { Text("Delete permanently?") },
            text = { Text("This message will be deleted for good.") },
            confirmButton = { TextButton(onClick = { confirmForever = false; vm.deleteOpen() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(m: Mail, folder: Folder) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(m.id) { mutableStateOf(false) }
    val outgoing = folder == Folder.SENT
    val p = parseAddr(m.from)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(m.subject, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.from, false, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label(m.from), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                TextButton(onClick = { expanded = !expanded }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier) {
                    Text(
                        if (outgoing) "to " + (m.to.firstOrNull()?.let { label(it) } ?: "nobody") else "to me",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
            Text(fullDate(m.date), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        if (expanded) {
            Column(Modifier.padding(top = 4.dp)) {
                DetailLine("From", if (p.name.isBlank()) p.addr else "${p.name} <${p.addr}>")
                if (m.to.isNotEmpty()) DetailLine("To", m.to.joinToString(", "))
                if (m.cc.isNotEmpty()) DetailLine("Cc", m.cc.joinToString(", "))
                if (m.bcc.isNotEmpty()) DetailLine("Bcc", m.bcc.joinToString(", "))
            }
        }
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attachments(vm: MailViewModel, m: Mail) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        m.attachments.forEachIndexed { n, a ->
            key(n) {
                var open by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { open = true },
                        label = { Text("${a.filename} · ${fmtSize(a.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text("Open") }, onClick = { open = false; vm.openAttachment(m, n, false) })
                        DropdownMenuItem(
                            text = { Text("Save to Downloads") }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                            onClick = { open = false; vm.openAttachment(m, n, true) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardDialog(onDismiss: () -> Unit, onSend: (List<String>) -> Unit, onEdit: () -> Unit) {
    var to by remember { mutableStateOf("") }
    val list = splitAddrs(to)
    val ok = list.isNotEmpty() && list.size <= 5 && list.all { validAddr(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward") },
        text = {
            Column {
                Text("Send this message on as it is, or edit it first.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(list) }, enabled = ok) { Text("Send") } },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Edit first") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** The message body in a locked-down WebView: no scripting, links leave the app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MailWebView(m: Mail, modifier: Modifier, onLink: (Uri) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val bg = cs.surface.toArgb()
    val onLinkNow by rememberUpdatedState(onLink)
    val page = remember(m.id, m.html, m.text, dark) { buildPage(m, dark, cs.onSurface.toArgb(), cs.primary.toArgb()) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setBackgroundColor(bg)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        onLinkNow(request.url)
                        return true
                    }
                }
            }
        },
        update = { wv ->
            wv.setBackgroundColor(bg)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, dark)
            }
            if (wv.tag != page) {
                wv.tag = page
                wv.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

private fun buildPage(m: Mail, dark: Boolean, text: Int, link: Int): String {
    val html = m.html
    val head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>img{max-width:100%;height:auto}body{word-wrap:break-word;overflow-wrap:anywhere}</style>"
    if (!html.isNullOrBlank()) return "<!doctype html><html><head><meta charset=\"utf-8\">$head</head><body>$html</body></html>"
    return "<!doctype html><html><head><meta charset=\"utf-8\">$head<style>" +
        "body{margin:16px;font:16px/1.5 sans-serif;color:${hex(text)};background:transparent}a{color:${hex(link)}}" +
        "pre{white-space:pre-wrap;word-wrap:break-word;font:inherit;margin:0}</style></head>" +
        "<body><pre>${escHtml(m.text.orEmpty())}</pre></body></html>"
}
