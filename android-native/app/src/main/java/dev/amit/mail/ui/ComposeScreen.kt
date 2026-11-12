@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.amit.mail.data.fmtSize
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(vm: MailViewModel) {
    val c = vm.compose ?: return
    val cs = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { vm.attach(it) }
    var menu by remember { mutableStateOf(false) }
    var templateDialog by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var fromMenu by remember { mutableStateOf(false) }

    // Saved as you type: two quiet seconds after the last edit.
    LaunchedEffect(c.edits) {
        if (c.edits > 0) {
            delay(2000)
            vm.saveDraft(c)
        }
    }
    LaunchedEffect(templateDialog) { if (templateDialog) vm.loadTemplates(force = true) }

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text(c.title) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.Outlined.Close, "Close and save draft") } },
                actions = {
                    IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Outlined.AttachFile, "Attach files") }
                    IconButton(onClick = { vm.send() }, enabled = !c.sending) {
                        if (c.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = cs.primary)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (c.htmlMode) "Switch to plain text" else "Write as HTML") },
                            leadingIcon = { Icon(Icons.Outlined.Code, null) },
                            onClick = { menu = false; c.htmlMode = !c.htmlMode; c.markDirty() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add a template") }, leadingIcon = { Icon(Icons.Outlined.Newspaper, null) },
                            onClick = { menu = false; templateDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Discard draft") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { menu = false; confirmDiscard = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).imePadding().fillMaxSize().verticalScroll(rememberScrollState())) {
            // From: the primary address, another alias, or any name@domain typed for this one mail
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("From", Modifier.width(48.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                PlainField(
                    value = c.from, onChange = { c.from = it; c.markDirty() },
                    placeholder = vm.aliases.firstOrNull().orEmpty(), modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Email,
                )
                if (vm.aliases.size > 1) {
                    IconButton(onClick = { fromMenu = true }) { Icon(Icons.Outlined.ArrowDropDown, "Choose address") }
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            Recipients("To", c.to, c.toInput, onChange = { l, i -> c.to = l; c.toInput = i; c.markDirty() }) {
                if (!c.showCc) TextButton(onClick = { c.showCc = true }) { Text("Cc/Bcc") }
            }
            HorizontalDivider(color = cs.outlineVariant)
            if (c.showCc) {
                Recipients("Cc", c.cc, c.ccInput, onChange = { l, i -> c.cc = l; c.ccInput = i; c.markDirty() })
                HorizontalDivider(color = cs.outlineVariant)
                Recipients("Bcc", c.bcc, c.bccInput, onChange = { l, i -> c.bcc = l; c.bccInput = i; c.markDirty() })
                HorizontalDivider(color = cs.outlineVariant)
            }
            PlainField(
                value = c.subject, onChange = { c.subject = it.replace("\n", " "); c.markDirty() }, placeholder = "Subject",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            HorizontalDivider(color = cs.outlineVariant)
            TextField(
                value = c.bodyField, onValueChange = { c.bodyField = it; c.markDirty() },
                placeholder = { Text(if (c.htmlMode) "HTML" else "Write your message") },
                minLines = 10, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.let { if (c.htmlMode) it.copy(fontFamily = FontFamily.Monospace) else it },
                colors = transparentFieldColors(),
            )
            if (c.templateHtml.isNotEmpty() || c.files.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.templateHtml.isNotEmpty()) {
                        InputChip(
                            selected = true, onClick = { templateDialog = true },
                            label = { Text("Template: ${c.templateName.ifBlank { "added" }}") },
                            trailingIcon = { IconButton(onClick = { vm.chooseTemplate(null) }, Modifier.size(18.dp)) { Icon(Icons.Outlined.Close, "Remove template") } },
                        )
                    }
                    c.files.forEach { f ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(f.name + if (f.size > 0) " · ${fmtSize(f.size)}" else "", maxLines = 1, modifier = Modifier.widthIn(max = 220.dp)) },
                            leadingIcon = {
                                if (f.uploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                            },
                            trailingIcon = { IconButton(onClick = { vm.removeFile(f) }, Modifier.size(18.dp)) { Icon(Icons.Outlined.Close, "Remove file") } },
                        )
                    }
                }
            }
            if (c.status.isNotEmpty()) {
                Text(c.status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }

    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    if (templateDialog) {
        ModalBottomSheet(onDismissRequest = { templateDialog = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Add a template", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                if (vm.templates.isEmpty()) {
                    Text(
                        "No templates yet. Create them in the web app.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                vm.templates.forEach { t ->
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = if (t.isDefault) ({ Text("Default") }) else null,
                        leadingContent = { Icon(Icons.Outlined.Newspaper, null) },
                        colors = clear,
                        modifier = Modifier.clickable { templateDialog = false; vm.chooseTemplate(t) },
                    )
                }
            }
        }
    }
    if (fromMenu) {
        ModalBottomSheet(onDismissRequest = { fromMenu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Send from", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                vm.aliases.forEachIndexed { i, a ->
                    ListItem(
                        headlineContent = { Text(a) },
                        supportingContent = if (i == 0) ({ Text("Primary") }) else null,
                        colors = clear,
                        modifier = Modifier.clickable { fromMenu = false; c.from = a; c.markDirty() },
                    )
                }
                Text(
                    "Or type any name@${vm.aliases.firstOrNull()?.substringAfter('@', "").orEmpty()} in the From field. It is not saved.",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this draft?") },
            text = { Text("The message and its files are deleted.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discardDraft() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun PlainField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier,
        placeholder = { Text(placeholder) }, colors = transparentFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
    )
}

/** Addresses as chips; typing a comma, space or Enter turns the text into a chip. Invalid ones show in red. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Recipients(
    label: String, list: List<String>, input: String, onChange: (List<String>, String) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(48.dp).padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        FlowRow(
            Modifier.weight(1f).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            list.forEach { a ->
                InputChip(
                    selected = false, onClick = { onChange(list - a, input) },
                    label = { Text(a, maxLines = 1, modifier = Modifier.widthIn(max = 240.dp)) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove $a", Modifier.size(16.dp)) },
                    colors = if (validAddr(a)) InputChipDefaults.inputChipColors() else InputChipDefaults.inputChipColors(labelColor = cs.error),
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { v ->
                    if (v.any { it == ',' || it == ';' || it == ' ' || it == '\n' } && v.isNotBlank()) onChange((list + splitAddrs(v)).distinct(), "")
                    else onChange(list, v)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.widthIn(min = 120.dp).padding(vertical = 10.dp).onFocusChanged {
                    if (!it.isFocused && input.isNotBlank()) onChange((list + splitAddrs(input)).distinct(), "")
                },
            )
        }
        trailing()
    }
}
