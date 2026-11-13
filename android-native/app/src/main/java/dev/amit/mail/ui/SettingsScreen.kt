@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import dev.amit.mail.data.fmtSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.amit.mail.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MailViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshStorage() }
    val transparent = ListItemDefaults.colors(containerColor = cs.surface)
    // A large top bar that collapses into a small one as the settings scroll.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = cs.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { BackButton { vm.back() } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer),
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Account")
            ListItem(
                headlineContent = { Text(vm.email.ifBlank { "Signed in" }) },
                supportingContent = { Text("Google account") },
                leadingContent = { Avatar(vm.email, false) },
                colors = transparent,
            )
            OutlinedButton(onClick = { confirmSignOut = true }, Modifier.padding(horizontal = 16.dp)) { Text("Sign out") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Section("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                leadingContent = { Icon(Icons.Outlined.Palette, null) },
                colors = transparent,
            )
            val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                options.forEachIndexed { i, (key, name) ->
                    SegmentedButton(
                        selected = vm.theme == key, onClick = { vm.updateTheme(key) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(name) }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                ListItem(
                    headlineContent = { Text("Dynamic colour") },
                    supportingContent = { Text("Use colours from your wallpaper (Material You)") },
                    trailingContent = { Switch(checked = vm.dynamicColor, onCheckedChange = { vm.setDynamic(it) }) },
                    modifier = Modifier.clickable { vm.setDynamic(!vm.dynamicColor) },
                    colors = transparent,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Section("Notifications")
            ListItem(
                headlineContent = { Text("Send a test notification") },
                supportingContent = { Text("Checks that new mail can reach this phone") },
                leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) },
                modifier = Modifier.clickable { vm.testPush() },
                colors = transparent,
            )
            ListItem(
                headlineContent = { Text("Notification settings") },
                supportingContent = { Text("Sound, banners and lock screen") },
                leadingContent = { Icon(Icons.Outlined.Notifications, null) },
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                },
                colors = transparent,
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Section("Storage")
            ListItem(
                headlineContent = { Text("Downloaded files") },
                supportingContent = {
                    Text(if (vm.fileCount > 0) "${vm.fileCount} file${if (vm.fileCount == 1) "" else "s"} \u00B7 ${fmtSize(vm.fileBytes)}" else "None saved")
                },
                leadingContent = { Icon(Icons.Outlined.Storage, null) },
                colors = transparent,
            )
            LinearProgressIndicator(
                progress = { (vm.fileBytes.toFloat() / vm.fileCap).coerceIn(0f, 1f).let { if (vm.fileBytes > 0 && it < 0.02f) 0.02f else it } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = if (vm.fileBytes >= vm.fileCap * 85 / 100) cs.error else cs.primary,
            )
            Text(
                "Up to ${fmtSize(vm.fileCap)} on this phone. Older files go first when it is full.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmClear = true }, enabled = vm.fileCount > 0, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear downloaded files")
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Section("About")
            ListItem(
                headlineContent = { Text("Mailbox ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text(BuildConfig.BASE_URL.removePrefix("https://")) },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                colors = transparent,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear downloaded files?") },
            text = { Text("${vm.fileCount} file${if (vm.fileCount == 1) "" else "s"} (${fmtSize(vm.fileBytes)}) will be removed from this phone. Your mail stays, and a file downloads again when you open it.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearFiles() }) { Text("Clear files") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Mail saved on this phone is removed. You can sign in again any time.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut(ctx as? Activity) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
