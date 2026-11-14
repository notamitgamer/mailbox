@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import dev.amit.mail.data.fmtDate
import dev.amit.mail.data.initialOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    val (avBg, avFg) = avatarColors(cs, m?.from)
    val colors = PageColors(
        cs.surface.toArgb(), card.toArgb(), cs.onSurface.toArgb(), cs.onSurfaceVariant.toArgb(), cs.secondaryContainer.toArgb(),
        cs.onSecondaryContainer.toArgb(), cs.primary.toArgb(), avBg.toArgb(), avFg.toArgb(), cs.outlineVariant.toArgb(),
    )
    var menu by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    var fileSheet by remember { mutableStateOf<Int?>(null) }
    var darkView by remember(route.id) { mutableStateOf(true) }
    var headerVisible by remember(route.id) { mutableStateOf(true) }
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
            if (m.hasBody) {
                MailWebView(
                    m, folder, invert = dark && darkView, c = colors, modifier = Modifier.weight(1f).fillMaxWidth(),
                    onAttachment = { fileSheet = it },
                    // The subject moves up into the top bar once it has scrolled out of the page.
                    onScroll = { y, _ -> headerVisible = y < (56 * ctx.resources.displayMetrics.density) },
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

/** Colours handed to the mail page (as ARGB), so the header and card match the app theme. */
private data class PageColors(
    val surface: Int, val card: Int, val text: Int, val textVar: Int, val chipBg: Int,
    val chipFg: Int, val link: Int, val avBg: Int, val avFg: Int, val line: Int,
)

/** The whole message, header included, in one locked-down WebView, so it all scrolls together like Gmail. No scripting; links leave the app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MailWebView(
    m: Mail, folder: Folder, invert: Boolean, c: PageColors, modifier: Modifier,
    onScroll: (Int, Int) -> Unit, onLink: (Uri) -> Unit, onAttachment: (Int) -> Unit,
) {
    val bg = c.surface
    val onLinkNow by rememberUpdatedState(onLink)
    val onScrollNow by rememberUpdatedState(onScroll)
    val onAttachmentNow by rememberUpdatedState(onAttachment)
    val page = remember(m.id, m.html, m.text, folder, invert, c) { buildPage(m, folder, invert, c) }
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
                        val u = request.url
                        if (u.scheme == "att") u.host?.toIntOrNull()?.let { onAttachmentNow(it) } else onLinkNow(u)
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

private fun buildPage(m: Mail, folder: Folder, invert: Boolean, c: PageColors): String {
    val isHtml = !m.html.isNullOrBlank()
    val flip = isHtml && invert
    // Dark view: the message block is inverted (hues turned back, pictures un-inverted); its background is chosen so it
    // inverts to the card colour. The filter sits on that block, not on <html>, so the page background stays right.
    val msgBg = if (flip) hex(0xFFFFFF xor (c.card and 0xFFFFFF)) else hex(c.card)
    val inv = "filter:invert(1) hue-rotate(180deg)"
    val css = "html{background:${hex(c.surface)}}" +
        "body{margin:0;padding:0 12px 12px;font-family:sans-serif;color:${hex(c.text)};overflow-wrap:anywhere}" +
        "img{max-width:100%;height:auto}a{color:${hex(c.link)}}" +
        ".subj{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:8px 4px 14px;font-size:24px;line-height:32px;color:${hex(c.text)}}" +
        ".chip{font-size:12px;line-height:16px;padding:2px 8px;border-radius:6px;background:${hex(c.chipBg)};color:${hex(c.chipFg)}}" +
        ".top{background:${hex(c.card)};border-radius:24px 24px 0 0;padding:16px 16px 8px}" +
        ".row{display:flex;align-items:flex-start;gap:12px}" +
        ".av{width:40px;height:40px;border-radius:50%;background:${hex(c.avBg)};color:${hex(c.avFg)};display:flex;align-items:center;justify-content:center;font-size:18px;flex:none}" +
        ".who{flex:1;min-width:0}.l1{display:flex;align-items:baseline;gap:8px}" +
        ".nm{font-size:16px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        ".tm{font-size:12px;color:${hex(c.textVar)};white-space:nowrap}" +
        "summary{list-style:none;cursor:pointer;color:${hex(c.textVar)};font-size:14px;padding:2px 0}" +
        "summary::-webkit-details-marker{display:none}summary:after{content:' \\25BE'}details[open] summary:after{content:' \\25B4'}" +
        ".det{font-size:13px;line-height:20px;padding:6px 0 0}.det b{display:inline-block;width:44px;font-weight:400;color:${hex(c.textVar)}}" +
        ".atts{display:flex;flex-wrap:wrap;gap:8px;padding-top:10px}" +
        ".att{font-size:13px;padding:6px 12px;border:1px solid ${hex(c.line)};border-radius:8px;color:${hex(c.text)};text-decoration:none;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        ".msg{background:$msgBg;border-radius:0 0 24px 24px;padding:8px 8px 24px;${if (flip) inv else ""}}" +
        (if (flip) ".msg img,.msg video,.msg picture,.msg svg,.msg canvas,.msg iframe,.msg [style*=\"background-image\"]{$inv}" else "") +
        "pre{white-space:pre-wrap;word-wrap:break-word;font:16px/1.5 sans-serif;margin:0;padding:8px}"
    val p = parseAddr(m.from)
    val outgoing = folder == Folder.SENT
    val who = if (outgoing) "to " + (m.to.firstOrNull()?.let { label(it) } ?: "nobody") else "to me"
    val details = StringBuilder()
    details.append("<div><b>From</b>").append(escHtml(if (p.name.isBlank()) p.addr else "${p.name} <${p.addr}>")).append("</div>")
    if (m.to.isNotEmpty()) details.append("<div><b>To</b>").append(escHtml(m.to.joinToString(", "))).append("</div>")
    if (m.cc.isNotEmpty()) details.append("<div><b>Cc</b>").append(escHtml(m.cc.joinToString(", "))).append("</div>")
    if (m.bcc.isNotEmpty()) details.append("<div><b>Bcc</b>").append(escHtml(m.bcc.joinToString(", "))).append("</div>")
    details.append("<div><b>Date</b>").append(escHtml(fullDate(m.date))).append("</div>")
    val atts = StringBuilder()
    if (m.attachments.isNotEmpty() && folder != Folder.DRAFTS) {
        atts.append("<div class=\"atts\">")
        m.attachments.forEachIndexed { n, a -> atts.append("<a class=\"att\" href=\"att://$n\">").append(escHtml("${a.filename} \u00B7 ${fmtSize(a.size)}")).append("</a>") }
        atts.append("</div>")
    }
    val chip = if (folder == Folder.INBOX) "<span class=\"chip\">Inbox</span>" else ""
    val header = "<div class=\"subj\"><span>${escHtml(m.subject)}</span>$chip</div>" +
        "<div class=\"top\"><div class=\"row\"><div class=\"av\">${escHtml(initialOf(m.from))}</div><div class=\"who\">" +
        "<div class=\"l1\"><span class=\"nm\">${escHtml(label(m.from))}</span><span class=\"tm\">${escHtml(fmtDate(m.date))}</span></div>" +
        "<details><summary>${escHtml(who)}</summary><div class=\"det\">$details</div></details></div></div>$atts</div>"
    val body = if (isHtml) m.html.orEmpty() else "<pre>${escHtml(m.text.orEmpty())}</pre>"
    return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>$css</style></head><body>$header<div class=\"msg\">$body</div></body></html>"
}
