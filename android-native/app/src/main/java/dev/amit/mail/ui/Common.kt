@file:Suppress("DEPRECATION")

package dev.amit.mail.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.amit.mail.data.Folder
import dev.amit.mail.data.initialOf
import dev.amit.mail.data.label
import kotlin.math.abs

fun folderIcon(f: Folder): ImageVector = when (f) {
    Folder.INBOX -> Icons.Outlined.Inbox
    Folder.SENT -> Icons.Outlined.Send
    Folder.DRAFTS -> Icons.Outlined.Drafts
    Folder.TRASH -> Icons.Outlined.DeleteOutline
}

/** Material 3 window width classes: under 600dp, 600 to 839dp, 840dp and up. */
enum class WindowSize { Compact, Medium, Expanded }

@Composable
fun windowSize(): WindowSize {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WindowSize.Expanded
        w >= 600 -> WindowSize.Medium
        else -> WindowSize.Compact
    }
}

// ---- container transform between a list row and the message screen ----

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks this element as the mail's container: the row and the opened message share these bounds and morph. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMail(id: String): Modifier {
    val shared = LocalSharedScope.current
    val anim = LocalAnimScope.current
    if (shared == null || anim == null) return this
    return with(shared) {
        this@sharedMail.sharedBounds(rememberSharedContentState(key = "mail_$id"), animatedVisibilityScope = anim)
    }
}

// ---- avatars use the colour scheme's own tonal pairs, so they follow dark mode and Material You ----

private fun avatarColors(cs: ColorScheme, who: String?): Pair<Color, Color> = when (abs(label(who).lowercase().hashCode()) % 6) {
    0 -> cs.primaryContainer to cs.onPrimaryContainer
    1 -> cs.secondaryContainer to cs.onSecondaryContainer
    2 -> cs.tertiaryContainer to cs.onTertiaryContainer
    3 -> cs.primary to cs.onPrimary
    4 -> cs.secondary to cs.onSecondary
    else -> cs.tertiary to cs.onTertiary
}

/** A round sender avatar: the initial on a tonal colour taken from the sender, or a check mark when selected. */
@Composable
fun Avatar(who: String?, selected: Boolean, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = if (selected) cs.primary to cs.onPrimary else avatarColors(cs, who)
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = fg)
        } else {
            Text(initialOf(who), style = MaterialTheme.typography.titleMedium, color = fg)
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
