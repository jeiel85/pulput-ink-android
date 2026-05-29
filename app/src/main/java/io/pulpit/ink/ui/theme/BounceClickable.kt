package io.pulpit.ink.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Drop-in premium replacement for Modifier.clickable that implements a natural, spring-physics
 * scale down micro-interaction on press (94% scale) and a subtle mechanical click haptic vibration
 * pop using local tactile feedback hardware. Supports optional onLongClick actions.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed {
    val context = LocalContext.current
    val hapticEnabledState = remember(context) {
        val prefs = context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("haptic_feedback_enabled", true))
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "haptic_feedback_enabled") {
                hapticEnabledState.value = prefs.getBoolean("haptic_feedback_enabled", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 400f
        ),
        label = "bounce_scale"
    )
    val haptic = LocalHapticFeedback.current

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = {
                if (hapticEnabledState.value) {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (e: Exception) {
                        // Safe fallback if device motor lacks precision support
                    }
                }
                onClick()
            },
            onLongClick = onLongClick?.let {
                {
                    if (hapticEnabledState.value) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (e: Exception) {
                            // Safe fallback
                        }
                    }
                    it()
                }
            }
        )
}

/**
 * Convenience single onClick bounceClickable extension.
 */
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
) = bounceClickable(
    enabled = enabled,
    onLongClick = null,
    onClick = onClick
)

