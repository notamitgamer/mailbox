@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.amit.mail.data.Folder
import dev.amit.mail.data.hueOf
import dev.amit.mail.data.initialOf

fun folderIcon(f: Folder): ImageVector = when (f) {
    Folder.INBOX -> Icons.Outlined.Inbox
    Folder.SENT -> Icons.Outlined.Send
    Folder.DRAFTS -> Icons.Outlined.Drafts
    Folder.TRASH -> Icons.Outlined.DeleteOutline
}

/** A round sender avatar: the initial on a hue taken from the sender, or a check mark when selected. */
@Composable
fun Avatar(who: String?, selected: Boolean, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val bg = if (selected) cs.primary else Color.hsv(hueOf(who), if (dark) 0.35f else 0.28f, if (dark) 0.42f else 0.92f)
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = cs.onPrimary)
        } else {
            Text(initialOf(who), style = MaterialTheme.typography.titleMedium, color = if (dark) Color.White.copy(alpha = 0.92f) else Color(0xFF1B1E21))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
        Text(hint, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}
