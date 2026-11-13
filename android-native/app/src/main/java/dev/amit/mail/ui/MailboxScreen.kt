@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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

private fun matches(m: Mail, q: String) =
    (m.from + " " + m.to.joinToString(" ") + " " + m.subject + " " + m.snippet).lowercase().contains(q.lowercase())

private fun badgeText(n: Int) = if (n > 99) "99+" else n.toString()

private fun Mail.isOpenIn(vm: MailViewModel) = vm.twoPane && vm.reader?.id == id && vm.stack.any { it is Route.Reader }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScreen(vm: MailViewModel, size: WindowSize) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selecting = vm.selection.isNotEmpty()
    val idle = vm.stack.isEmpty()
    var searchExpanded by remember { mutableStateOf(false) }

    // The last handler that is enabled wins, so the most specific one goes last.
    BackHandler(enabled = idle && vm.folder != Folder.INBOX) { vm.selectFolder(Folder.INBOX) }
    BackHandler(enabled = idle && vm.query.isNotEmpty()) { vm.query = "" }
    BackHandler(enabled = idle && selecting) { vm.selection = emptySet() }
    BackHandler(enabled = searchExpanded) { searchExpanded = false }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    when (size) {
        WindowSize.Compact -> ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !selecting && !searchExpanded,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "Mailbox", Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                    )
                    Folder.entries.forEach { f ->
                        val count = when (f) { Folder.INBOX -> vm.unread; Folder.DRAFTS -> vm.draftCount; else -> 0 }
                        NavigationDrawerItem(
                            label = { Text(f.title) },
                            icon = { Icon(folderIcon(f), null) },
                            badge = { if (count > 0) Text(badgeText(count), style = MaterialTheme.typography.labelLarge) },
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
            ListPane(vm, size, searchExpanded, { searchExpanded = it }, onMenu = { scope.launch { drawerState.open() } }, Modifier.fillMaxSize())
        }

        else -> Row(Modifier.fillMaxSize()) {
            MailRail(vm)
            val listModifier = if (size == WindowSize.Expanded) Modifier.width(400.dp).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()
            ListPane(vm, size, searchExpanded, { searchExpanded = it }, onMenu = null, listModifier)
            if (size == WindowSize.Expanded) {
                VerticalDivider()
                DetailPane(vm, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Navigation rail for medium and wide windows, with the compose button on top and badges on the folder icons. */
@Composable
private fun MailRail(vm: MailViewModel) {
    NavigationRail(
        header = {
            FloatingActionButton(
                onClick = { vm.openCompose(ComposeArgs()) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Icon(Icons.Filled.Edit, "Compose") }
        },
    ) {
        Spacer(Modifier.height(4.dp))
        Folder.entries.forEach { f ->
            val count = when (f) { Folder.INBOX -> vm.unread; Folder.DRAFTS -> vm.draftCount; else -> 0 }
            NavigationRailItem(
                selected = vm.folder == f,
                onClick = { vm.selectFolder(f) },
                icon = {
                    BadgedBox(badge = { if (count > 0) Badge { Text(badgeText(count)) } }) { Icon(folderIcon(f), f.title) }
                },
                label = { Text(f.title) },
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(selected = false, onClick = { vm.openSettings() }, icon = { Icon(Icons.Outlined.Settings, "Settings") }, label = { Text("Settings") })
    }
}

/** The right-hand side on wide windows: the open message, or a hint. */
@Composable
private fun DetailPane(vm: MailViewModel, modifier: Modifier) {
    val r = vm.stack.lastOrNull { it is Route.Reader } as? Route.Reader
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        if (r != null) {
            ReaderScreen(vm, r, embedded = true)
        } else {
            EmptyState(folderIcon(Folder.INBOX), "Select a message", "Pick a message from the list to read it here.", Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPane(
    vm: MailViewModel, size: WindowSize, searchExpanded: Boolean, setSearchExpanded: (Boolean) -> Unit,
    onMenu: (() -> Unit)?, modifier: Modifier,
) {
    val listState = vm.listState
    val selecting = vm.selection.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val visible = remember(vm.mails, vm.query, vm.unreadOnly) {
        val q = vm.query.trim()
        vm.mails.filter { (!vm.unreadOnly || !it.read) && (q.isEmpty() || matches(it, q)) }
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
    val barSpace = 72.dp // the Scaffold already pads for the status bar; this is just the search bar (56dp) plus its margins

    Box(modifier) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            floatingActionButton = {
                if (size == WindowSize.Compact && !selecting) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.openCompose(ComposeArgs()) },
                        expanded = fabExpanded,
                        icon = { Icon(Icons.Filled.Edit, null) },
                        text = { Text("Compose") },
                    )
                }
            },
        ) { inner ->
            PullToRefreshBox(
                isRefreshing = vm.refreshing, onRefresh = { vm.refresh() },
                modifier = Modifier.padding(inner).padding(top = barSpace).fillMaxSize(),
            ) {
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
                    itemsIndexed(rows, key = { _, it -> if (it is Mail) it.id else "h_$it" }, contentType = { _, it -> if (it is Mail) 0 else 1 }) { i, row ->
                        if (row is Mail) {
                            MailItem(vm, row, first = i == 0 || rows[i - 1] !is Mail, last = i == rows.lastIndex || rows[i + 1] !is Mail)
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

        if (selecting) {
            val chosen = vm.mails.filter { it.id in vm.selection }
            TopAppBar(
                modifier = Modifier.align(Alignment.TopCenter),
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
            MailSearchBar(vm, searchExpanded, setSearchExpanded, onMenu, Modifier.align(Alignment.TopCenter))
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

/** The Material 3 search bar: collapsed it is a pill; tapped it opens the search view with recent searches and matches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailSearchBar(
    vm: MailViewModel, expanded: Boolean, setExpanded: (Boolean) -> Unit, onMenu: (() -> Unit)?, modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val pad by animateDpAsState(if (expanded) 0.dp else 16.dp, label = "searchPad")
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = vm.query,
                onQueryChange = { vm.query = it },
                onSearch = { vm.commitSearch(it); setExpanded(false) },
                expanded = expanded,
                onExpandedChange = setExpanded,
                placeholder = { Text("Search in ${vm.folder.title.lowercase()}") },
                leadingIcon = {
                    when {
                        expanded -> IconButton(onClick = { setExpanded(false) }) { Icon(Icons.Outlined.ArrowBack, "Close search") }
                        onMenu != null -> IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "Menu") }
                        else -> Icon(Icons.Outlined.Search, null)
                    }
                },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
                    } else if (!expanded) {
                        IconButton(onClick = { vm.openSettings() }) { Avatar(vm.email, false, 32.dp) }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = setExpanded,
        modifier = modifier.fillMaxWidth().padding(horizontal = pad),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val q = vm.query.trim()
            if (q.isEmpty()) {
                if (vm.recentSearches.isEmpty()) {
                    Text(
                        "Type a name, a subject or a word.", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        TextButton(onClick = { vm.clearRecent() }) { Text("Clear") }
                    }
                    vm.recentSearches.forEach { r ->
                        ListItem(
                            headlineContent = { Text(r) },
                            leadingContent = { Icon(Icons.Outlined.History, null) },
                            trailingContent = { IconButton(onClick = { vm.removeRecent(r) }) { Icon(Icons.Outlined.Close, "Remove") } },
                            colors = transparent,
                            modifier = Modifier.clickable { vm.query = r; vm.commitSearch(r); setExpanded(false) },
                        )
                    }
                }
            } else {
                ListItem(
                    headlineContent = { Text("Search for \u201C$q\u201D") },
                    leadingContent = { Icon(Icons.Outlined.Search, null) },
                    colors = transparent,
                    modifier = Modifier.clickable { vm.commitSearch(q); setExpanded(false) },
                )
                vm.mails.filter { matches(it, q) }.take(6).forEach { m ->
                    ListItem(
                        headlineContent = { Text(label(m.from), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(m.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(m.from, false) },
                        colors = transparent,
                        modifier = Modifier.clickable { vm.commitSearch(q); setExpanded(false); vm.openMail(m) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MailItem(vm: MailViewModel, m: Mail, first: Boolean, last: Boolean) {
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
        val highlighted = selected || m.isOpenIn(vm)
        val container = when {
            highlighted -> cs.secondaryContainer
            m.read && folder == Folder.INBOX -> cs.surfaceContainerLow
            else -> cs.surfaceContainer
        }
        // A stock Material 3 three-line list item: sender above, subject, then the preview.
        ListItem(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = { if (selecting) vm.toggleSelect(m.id) else vm.openMail(m) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleSelect(m.id) },
            ),
            colors = ListItemDefaults.colors(containerColor = container),
            leadingContent = { Avatar(if (outgoing) m.to.firstOrNull() else m.from, selected, modifier = Modifier.clickable { vm.toggleSelect(m.id) }) },
            overlineContent = {
                Text(
                    who, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface,
                    style = MaterialTheme.typography.titleSmall, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                )
            },
            headlineContent = {
                Text(m.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
            },
            supportingContent = { Text(previewOf(m), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        fmtDate(m.date), style = MaterialTheme.typography.labelMedium,
                        color = if (unread) cs.primary else cs.onSurfaceVariant, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.attachmentCount > 0) Icon(Icons.Outlined.AttachFile, "Has attachments", Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        if (unread) Badge()
                    }
                }
            },
        )
    }

    // Gmail-style groups: each day is one card stack, rounded at its top and bottom, flat and thinly separated in between.
    val edge = 24.dp
    val shape = RoundedCornerShape(topStart = if (first) edge else 0.dp, topEnd = if (first) edge else 0.dp, bottomStart = if (last) edge else 0.dp, bottomEnd = if (last) edge else 0.dp)
    Box(Modifier.sharedMail(m.id).padding(horizontal = 12.dp).padding(bottom = 2.dp).clip(shape)) {
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
}
