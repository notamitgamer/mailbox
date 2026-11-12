@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.amit.mail.data.Folder
import dev.amit.mail.data.Mail
import dev.amit.mail.data.dayLabel
import dev.amit.mail.data.fmtDate
import dev.amit.mail.data.label
import dev.amit.mail.data.previewOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MailboxScreen(vm: MailViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selecting = vm.selection.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    // The last handler that is enabled wins, so the most specific one goes last.
    BackHandler(enabled = vm.folder != Folder.INBOX) { vm.selectFolder(Folder.INBOX) }
    BackHandler(enabled = vm.query.isNotEmpty()) { vm.query = "" }
    BackHandler(enabled = selecting) { vm.selection = emptySet() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val visible = remember(vm.mails, vm.query, vm.unreadOnly) {
        val q = vm.query.trim().lowercase()
        vm.mails.filter {
            (!vm.unreadOnly || !it.read) &&
                (q.isEmpty() || (it.from + " " + it.to.joinToString(" ") + " " + it.subject + " " + it.snippet).lowercase().contains(q))
        }
    }
    val rows = remember(visible) {
        buildList<Any> {
            var last = ""
            visible.forEach { m ->
                val l = dayLabel(m.date)
                if (l != last) { add(l); last = l }
                add(m)
            }
        }
    }
    val rowCount by rememberUpdatedState(rows.size)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { idx ->
            if (idx >= rowCount - 5 && vm.query.isEmpty()) vm.loadMore()
        }
    }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selecting,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Mailbox", Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                )
                Folder.entries.forEach { f ->
                    NavigationDrawerItem(
                        label = { Text(f.title) },
                        icon = { Icon(folderIcon(f), null) },
                        badge = { if (f == Folder.INBOX && vm.unread > 0) Text(vm.unread.toString(), style = MaterialTheme.typography.labelLarge) },
                        selected = vm.folder == f,
                        onClick = { vm.selectFolder(f); scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; vm.openSettings() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                if (selecting) {
                    val chosen = vm.mails.filter { it.id in vm.selection }
                    TopAppBar(
                        title = { Text("${vm.selection.size} selected") },
                        navigationIcon = { IconButton(onClick = { vm.selection = emptySet() }) { Icon(Icons.Outlined.Close, "Clear selection") } },
                        actions = {
                            IconButton(onClick = { vm.selectAll(visible.map { it.id }) }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                            if (vm.folder == Folder.INBOX) {
                                val anyUnread = chosen.any { !it.read }
                                IconButton(onClick = { vm.setRead(vm.selection, anyUnread) }) {
                                    Icon(if (anyUnread) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread, if (anyUnread) "Mark read" else "Mark unread")
                                }
                            }
                            if (vm.folder == Folder.TRASH) {
                                IconButton(onClick = { vm.restoreMails(vm.selection) }) { Icon(Icons.Outlined.RestoreFromTrash, "Restore") }
                            }
                            IconButton(onClick = {
                                if (vm.folder == Folder.INBOX || vm.folder == Folder.SENT) vm.deleteMails(vm.selection) else confirmDelete = true
                            }) { Icon(Icons.Outlined.Delete, "Delete") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else {
                    SearchPill(vm, onMenu = { scope.launch { drawerState.open() } })
                }
            },
            floatingActionButton = {
                if (!selecting) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.openCompose(ComposeArgs()) },
                        expanded = fabExpanded,
                        icon = { Icon(Icons.Filled.Edit, null) },
                        text = { Text("Compose") },
                    )
                }
            },
        ) { inner ->
            PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.padding(inner).fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (vm.folder == Folder.INBOX) {
                        item(key = "filters") {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !vm.unreadOnly, onClick = { vm.unreadOnly = false }, label = { Text("All") })
                                FilterChip(selected = vm.unreadOnly, onClick = { vm.unreadOnly = true }, label = { Text("Unread") })
                            }
                        }
                    }
                    if (vm.folder == Folder.TRASH && vm.mails.isNotEmpty()) {
                        item(key = "binbar") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Mail in the bin is deleted after 30 days.", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { confirmEmpty = true }) { Text("Empty bin") }
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                when {
                                    vm.refreshing && vm.mails.isEmpty() -> CircularProgressIndicator()
                                    vm.query.isNotBlank() -> EmptyState(Icons.Outlined.SearchOff, "No matches", "Try a different word.")
                                    vm.unreadOnly -> EmptyState(Icons.Outlined.MarkEmailRead, "All caught up", "No unread messages.")
                                    else -> when (vm.folder) {
                                        Folder.INBOX -> EmptyState(folderIcon(Folder.INBOX), "Inbox zero", "Nothing new. Enjoy the quiet.")
                                        Folder.SENT -> EmptyState(folderIcon(Folder.SENT), "Nothing sent yet", "Messages you send will show up here.")
                                        Folder.DRAFTS -> EmptyState(folderIcon(Folder.DRAFTS), "No drafts", "Messages you start writing are saved here automatically.")
                                        Folder.TRASH -> EmptyState(folderIcon(Folder.TRASH), "Bin is empty", "Deleted mail stays here for 30 days.")
                                    }
                                }
                            }
                        }
                    }
                    items(rows, key = { if (it is Mail) it.id else "h_$it" }, contentType = { if (it is Mail) 0 else 1 }) { row ->
                        if (row is Mail) {
                            MailItem(vm, row)
                        } else {
                            Text(
                                row as String, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (vm.loadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        }
                    }
                    item(key = "pad") { Spacer(Modifier.height(88.dp)) } // room for the compose button
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete permanently?") },
            text = { Text("${vm.selection.size} message${if (vm.selection.size == 1) "" else "s"} will be deleted for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteMails(vm.selection) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text("Empty the bin?") },
            text = { Text("Everything in the bin is deleted for good.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; vm.emptyBin() }) { Text("Empty bin") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchPill(vm: MailViewModel, onMenu: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp),
        shape = CircleShape, color = cs.surfaceContainerHigh,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "Menu") }
            Box(Modifier.weight(1f)) {
                if (vm.query.isEmpty()) {
                    Text("Search in ${vm.folder.title.lowercase()}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
                BasicTextField(
                    value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.query.isNotEmpty()) {
                IconButton(onClick = { vm.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
            } else {
                Box(
                    Modifier.padding(end = 10.dp).size(36.dp).clip(CircleShape).background(cs.primaryContainer).clickable { vm.openSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(vm.email.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MailItem(vm: MailViewModel, m: Mail) {
    val haptic = LocalHapticFeedback.current
    val folder = vm.folder
    val selected = m.id in vm.selection
    val selecting = vm.selection.isNotEmpty()
    val current by rememberUpdatedState(m)
    val swipeable = (folder == Folder.INBOX || folder == Folder.SENT) && !selecting

    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> { vm.deleteMails(setOf(current.id)); true }
            SwipeToDismissBoxValue.StartToEnd -> { vm.setRead(setOf(current.id), current.read.not()); false }
            else -> false
        }
    })

    @Composable
    fun Content() {
        val cs = MaterialTheme.colorScheme
        val outgoing = folder == Folder.SENT || folder == Folder.DRAFTS
        val who = if (outgoing) (m.to.firstOrNull()?.let { "To: " + label(it) } ?: "(no recipient)") else label(m.from)
        val unread = !m.read && folder == Folder.INBOX
        Row(
            Modifier.fillMaxWidth()
                .background(if (selected) cs.secondaryContainer else cs.surface)
                .combinedClickable(
                    onClick = { if (selecting) vm.toggleSelect(m.id) else vm.openMail(m) },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleSelect(m.id) },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(if (outgoing) m.to.firstOrNull() else m.from, selected, modifier = Modifier.clickable { vm.toggleSelect(m.id) })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        who, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        fmtDate(m.date), style = MaterialTheme.typography.labelMedium,
                        color = if (unread) cs.primary else cs.onSurfaceVariant, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Text(
                    m.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, color = cs.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        previewOf(m), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    if (m.attachmentCount > 0) Icon(Icons.Outlined.AttachFile, "Has attachments", Modifier.padding(start = 8.dp).size(16.dp), tint = cs.onSurfaceVariant)
                    if (unread) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(cs.primary))
                }
            }
        }
    }

    if (swipeable) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = folder == Folder.INBOX,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                val cs = MaterialTheme.colorScheme
                val dir = state.dismissDirection
                val bg = when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> cs.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> cs.errorContainer
                    else -> Color.Transparent
                }
                Box(
                    Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                    contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    when (dir) {
                        SwipeToDismissBoxValue.StartToEnd -> Icon(if (m.read) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead, null, tint = cs.onPrimaryContainer)
                        SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Outlined.Delete, null, tint = cs.onErrorContainer)
                        else -> {}
                    }
                }
            },
        ) { Content() }
    } else {
        Content()
    }
}
