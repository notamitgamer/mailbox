package dev.amit.mail.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MailRoot(vm: MailViewModel, onSignIn: () -> Unit) {
    MailTheme(vm.theme, vm.dynamicColor) {
        val size = windowSize()
        SideEffect { vm.twoPane = size == WindowSize.Expanded }

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

        // Predictive back: the screen on top shrinks as you swipe, and goes away when you let go.
        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackHandler(enabled = vm.signedIn && vm.stack.isNotEmpty()) { progress ->
            try {
                progress.collect { backProgress = it.progress }
                vm.back()
                backProgress = 0f
            } catch (e: CancellationException) {
                backProgress = 0f
            }
        }

        // On wide windows the message opens beside the list, so it is not a screen on top there.
        val top = vm.stack.lastOrNull()
        val overlay = if (size == WindowSize.Expanded && top is Route.Reader) null else top

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (!vm.signedIn) {
                SignInScreen(busy = vm.signInBusy, error = vm.signInError, onSignIn = onSignIn)
            } else {
                SharedTransitionLayout {
                    val sharedScope = this
                    CompositionLocalProvider(LocalSharedScope provides sharedScope) {
                        AnimatedContent(
                            targetState = overlay,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                if (overlay != null) {
                                    val s = 1f - 0.1f * backProgress
                                    scaleX = s
                                    scaleY = s
                                    alpha = 1f - 0.2f * backProgress
                                }
                            },
                            transitionSpec = {
                                when {
                                    // The list row and the message share their bounds, so a fade is all the rest needs.
                                    targetState is Route.Reader || initialState is Route.Reader ->
                                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                                    targetState == null -> fadeIn() togetherWith (slideOutHorizontally { it / 8 } + fadeOut())
                                    else -> (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut()
                                }
                            },
                            label = "route",
                        ) { route ->
                            CompositionLocalProvider(LocalAnimScope provides this) {
                                when (route) {
                                    null -> MailboxScreen(vm, size)
                                    is Route.Reader -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { ReaderScreen(vm, route) }
                                    is Route.Compose -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { ComposeScreen(vm) }
                                    Route.Settings -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { SettingsScreen(vm) }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                snack,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                    .padding(bottom = if (vm.signedIn && vm.stack.isEmpty() && size == WindowSize.Compact) 80.dp else 0.dp),
            )
        }
    }
}
