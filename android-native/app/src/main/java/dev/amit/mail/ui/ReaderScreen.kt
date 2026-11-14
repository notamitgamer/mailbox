@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import dev.amit.mail.data.fmtDate
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ReplyAll
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun ReaderScreen(vm: MailViewModel, route: Route.Reader, embedded: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val card = cs.surfaceContainerLow
    val dark = cs.surface.luminance() < 0.5f
    val m = vm.reader
    var menu by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    var fileSheet by remember { mutableStateOf<Int?>(null) }
    var darkView by remember(route.id) { mutableStateOf(true) }
    var headerVisible by remember(route.id) { mutableStateOf(true) }
    var lastToggle by remember { mutableStateOf(0L) }
    val folder = route.folder
    val ctx = LocalContext.current

    Scaffold(
        modifier = if (embedded) Modifier else Modifier.sharedMail(route.id),
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = {
                    // The big subject collapses away while you read; the bar carries it instead.
                    AnimatedVisibility(visible = !headerVisible && m != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(m?.subject.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = { if (!embedded) BackButton { vm.back() } },
                actions = {
                    if (m != null) {
                        when (folder) {
                            Folder.INBOX -> {
                                IconButton(onClick = { vm.deleteOpen() }) { Icon(Icons.Outlined.Delete, "Delete") }
                                IconButton(onClick = { vm.markUnreadOpen() }) { Icon(Icons.Outlined.MarkEmailUnread, "Mark as unread") }
                            }
                            Folder.SENT -> IconButton(onClick = { vm.deleteOpen() }) { Icon(Icons.Outlined.Delete, "Delete") }
                            Folder.TRASH -> {
                                IconButton(onClick = { vm.restoreOpen() }) { Icon(Icons.Outlined.RestoreFromTrash, "Restore") }
                                IconButton(onClick = { confirmForever = true }) { Icon(Icons.Outlined.DeleteForever, "Delete forever") }
                            }
                            Folder.DRAFTS -> {}
                        }
                        // Gmail-style menu: roomy rows, outlined icons, groups split by dividers.
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                            DropdownMenu(
                                expanded = menu, onDismissRequest = { menu = false },
                                shape = RoundedCornerShape(12.dp), containerColor = cs.surfaceContainer,
                                tonalElevation = 0.dp, shadowElevation = 8.dp,
                                modifier = Modifier.widthIn(min = 280.dp),
                            ) {
                                val canReply = folder == Folder.INBOX
                                val canForward = folder != Folder.TRASH
                                if (canReply) GmailMenuItem("Reply all", Icons.Outlined.ReplyAll) { menu = false; vm.replyTo(true) }
                                if (canForward) GmailMenuItem("Forward", Icons.Outlined.Forward) { menu = false; forward = true }
                                if (dark) {
                                    if (canReply || canForward) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                                    GmailMenuItem(
                                        if (darkView) "View in light theme" else "View in dark theme",
                                        if (darkView) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    ) { menu = false; darkView = !darkView }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        },
        bottomBar = {
            // Reply and Forward stay pinned to the bottom of the screen, like Gmail, whatever the scroll position.
            val canAct = m != null && m.hasBody && (folder == Folder.INBOX || folder == Folder.SENT)
            if (canAct) {
                Row(
                    Modifier.fillMaxWidth().background(cs.surface).navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (folder == Folder.INBOX) {
                        FilledTonalButton(onClick = { vm.replyTo(false) }, Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Outlined.Reply, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reply")
                        }
                    }
                    FilledTonalButton(onClick = { forward = true }, Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Outlined.Forward, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Forward")
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (m == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            if (vm.readerLoading && !m.hasBody) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimatedVisibility(
                visible = headerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    SubjectRow(m, folder)
                    if (m.attachments.isNotEmpty() && folder != Folder.DRAFTS) Attachments(m) { fileSheet = it }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(24.dp)).background(card),
            ) {
            SenderRow(m, folder)
            if (m.hasBody) {
                MailWebView(
                    m, invert = dark && darkView, surface = card, modifier = Modifier.weight(1f).fillMaxWidth(),
                    onScroll = { y, oldY ->
                        val now = System.currentTimeMillis()
                        if (now - lastToggle > 350) {
                            val want = when {
                                y <= 4 -> true
                                y - oldY > 8 -> false
                                oldY - y > 8 -> true
                                else -> headerVisible
                            }
                            if (want != headerVisible) { headerVisible = want; lastToggle = now }
                        }
                    },
                    onLink = { uri ->
                        if (uri.scheme == "mailto") {
                            vm.openCompose(ComposeArgs(to = splitAddrs(uri.schemeSpecificPart.substringBefore('?'))))
                        } else if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "tel") {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                )
            }
            }
        }
        }
    }

    if (forward) ForwardSheet(onDismiss = { forward = false }, onSend = { forward = false; vm.forwardAsIs(it) }, onEdit = { forward = false; vm.forwardEdit() })
    val sheetIndex = fileSheet
    if (sheetIndex != null && m != null) {
        FileSheet(
            m, sheetIndex, onDismiss = { fileSheet = null },
            onOpen = { fileSheet = null; vm.openAttachment(m, sheetIndex, false) },
            onSave = { fileSheet = null; vm.openAttachment(m, sheetIndex, true) },
        )
    }
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
private fun GmailMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, null, Modifier.size(24.dp)) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun SubjectRow(m: Mail, folder: Folder) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            m.subject, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall,
            maxLines = 3, overflow = TextOverflow.Ellipsis, color = cs.onSurface,
        )
        if (folder == Folder.INBOX) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(cs.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Inbox", style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
            }
        }
    }
}

/** Gmail-style sender row: avatar, name and time on one line, "to me" with a chevron right under the name. */
@Composable
private fun SenderRow(m: Mail, folder: Folder) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(m.id) { mutableStateOf(false) }
    val outgoing = folder == Folder.SENT
    val p = parseAddr(m.from)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(m.from, false, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label(m.from), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(fmtDate(m.date), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (outgoing) "to " + (m.to.firstOrNull()?.let { label(it) } ?: "nobody") else "to me",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, if (expanded) "Hide details" else "Show details", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                DetailLine("From", if (p.name.isBlank()) p.addr else "${p.name} <${p.addr}>")
                if (m.to.isNotEmpty()) DetailLine("To", m.to.joinToString(", "))
                if (m.cc.isNotEmpty()) DetailLine("Cc", m.cc.joinToString(", "))
                if (m.bcc.isNotEmpty()) DetailLine("Bcc", m.bcc.joinToString(", "))
                DetailLine("Date", fullDate(m.date))
            }
        }
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attachments(m: Mail, onPick: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        m.attachments.forEachIndexed { n, a ->
            key(n) {
                AssistChip(
                    onClick = { onPick(n) },
                    label = { Text("${a.filename} \u00B7 ${fmtSize(a.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/** What to do with one attachment, as a Material 3 bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheet(m: Mail, n: Int, onDismiss: () -> Unit, onOpen: () -> Unit, onSave: () -> Unit) {
    val a = m.attachments.getOrNull(n) ?: return
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(a.filename, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(fmtSize(a.size)) },
                leadingContent = { Icon(Icons.Outlined.AttachFile, null) },
                colors = clear,
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Open") }, leadingContent = { Icon(Icons.Outlined.OpenInNew, null) },
                colors = clear, modifier = Modifier.clickableRow(onOpen),
            )
            ListItem(
                headlineContent = { Text("Save to Downloads") }, leadingContent = { Icon(Icons.Outlined.Download, null) },
                colors = clear, modifier = Modifier.clickableRow(onSave),
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** Forward as it is, or edit first: a bottom sheet with the address field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardSheet(onDismiss: () -> Unit, onSend: (List<String>) -> Unit, onEdit: () -> Unit) {
    var to by remember { mutableStateOf("") }
    val list = splitAddrs(to)
    val ok = list.isNotEmpty() && list.size <= 5 && list.all { validAddr(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding()) {
            Text("Forward", style = MaterialTheme.typography.titleLarge)
            Text(
                "Send this message on as it is, or edit it first.", Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onEdit) { Text("Edit first") }
                Button(onClick = { onSend(list) }, enabled = ok) { Text("Send") }
            }
        }
    }
}

/** The message body in a locked-down WebView: no scripting, links leave the app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MailWebView(m: Mail, invert: Boolean, surface: Color, modifier: Modifier, onScroll: (Int, Int) -> Unit, onLink: (Uri) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = surface.toArgb()
    val onLinkNow by rememberUpdatedState(onLink)
    val onScrollNow by rememberUpdatedState(onScroll)
    val page = remember(m.id, m.html, m.text, invert, bg) { buildPage(m, invert, bg, cs.onSurface.toArgb(), cs.primary.toArgb()) }
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
                setOnScrollChangeListener { _, _, y, _, oldY -> onScrollNow(y, oldY) }
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
            // Dark mail is done in the page itself (see buildPage), so it also works when the app theme differs from the system's.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, false)
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

private fun buildPage(m: Mail, invert: Boolean, surface: Int, text: Int, link: Int): String {
    val html = m.html
    val head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>img{max-width:100%;height:auto}body{word-wrap:break-word;overflow-wrap:anywhere}</style>"
    if (!html.isNullOrBlank()) {
        // Dark view: invert the page and turn the hues back, then un-invert pictures. The page background is chosen
        // so that it inverts to the app's surface colour.
        val dark = if (invert) {
            val src = 0xFFFFFF xor (surface and 0xFFFFFF)
            // The filter goes on <body>: a filter on <html> leaves the canvas background uninverted (light page, light text).
            "<style>html{background:${hex(surface)}}body{margin:0;padding:8px;min-height:100vh;box-sizing:border-box;background:${hex(src)};filter:invert(1) hue-rotate(180deg)}" +
                "img,video,picture,svg,canvas,iframe,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)}</style>"
        } else ""
        return "<!doctype html><html><head><meta charset=\"utf-8\">$head$dark</head><body>$html<div style=\"height:16px\"></div></body></html>"
    }
    return "<!doctype html><html><head><meta charset=\"utf-8\">$head<style>" +
        "body{margin:16px;font:16px/1.5 sans-serif;color:${hex(text)};background:transparent}a{color:${hex(link)}}" +
        "pre{white-space:pre-wrap;word-wrap:break-word;font:inherit;margin:0}</style></head>" +
        "<body><pre>${escHtml(m.text.orEmpty())}</pre><div style=\"height:16px\"></div></body></html>"
}
