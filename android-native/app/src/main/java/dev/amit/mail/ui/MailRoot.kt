package dev.amit.mail.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MailRoot(vm: MailViewModel, onSignIn: () -> Unit) {
    MailTheme(vm.theme, vm.dynamicColor) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                val r = snack.showSnackbar(
                    message = e.message, actionLabel = e.actionLabel,
                    duration = if (e.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (r == SnackbarResult.ActionPerformed) e.action?.invoke()
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (!vm.signedIn) {
                SignInScreen(busy = vm.signInBusy, error = vm.signInError, onSignIn = onSignIn)
            } else {
                // The list stays composed underneath, so its scroll position survives opening a message.
                MailboxScreen(vm)
                AnimatedContent(
                    targetState = vm.stack.lastOrNull(),
                    transitionSpec = {
                        if (targetState == null) fadeIn() togetherWith (slideOutHorizontally { it / 8 } + fadeOut())
                        else (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut()
                    },
                    label = "route",
                ) { route ->
                    when (route) {
                        null -> Spacer(Modifier.fillMaxSize())
                        is Route.Reader -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { ReaderScreen(vm, route) }
                        is Route.Compose -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { ComposeScreen(vm) }
                        Route.Settings -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { SettingsScreen(vm) }
                    }
                }
            }
            SnackbarHost(
                snack,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                    .padding(bottom = if (vm.signedIn && vm.stack.isEmpty()) 80.dp else 0.dp),
            )
        }
    }
}

