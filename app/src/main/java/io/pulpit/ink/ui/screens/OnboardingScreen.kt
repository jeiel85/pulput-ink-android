package io.pulpit.ink.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pulpit.ink.R
import io.pulpit.ink.data.api.AssetModelDelivery
import io.pulpit.ink.data.api.WhisperModelConfig
import io.pulpit.ink.ui.viewmodel.SermonViewModel

/**
 * First-launch onboarding. Explains the offline-transcription value proposition
 * and surfaces acquisition of the default (base) model — delivered automatically
 * via Play Asset Delivery, or downloaded directly as a sideload fallback.
 */
@Composable
fun OnboardingScreen(
    viewModel: SermonViewModel,
    onFinish: () -> Unit
) {
    val background = Color(0xFF1C1B1F)
    val deepInk = Color(0xFFE6E1E5)
    val primaryEmerald = Color(0xFFD0BCFF)
    val softSlate = Color(0xFFCAC4D0)

    val delivery by viewModel.baseDelivery.collectAsState()
    val downloadStates by viewModel.downloadState.collectAsState()
    val downloadProgresses by viewModel.downloadProgress.collectAsState()

    // Kick off model acquisition as soon as onboarding appears.
    LaunchedEffect(Unit) {
        viewModel.ensureBaseModelForOnboarding()
    }

    val baseState = downloadStates["base"] ?: "not_downloaded"
    val isReady = baseState == "downloaded" ||
        delivery?.status == AssetModelDelivery.Status.COMPLETED

    // Resolve a single user-facing status + progress from PAD / HF sources.
    val padStatus = delivery?.status
    val isWaitingWifi = padStatus == AssetModelDelivery.Status.WAITING_FOR_WIFI
    val padActive = padStatus == AssetModelDelivery.Status.DOWNLOADING ||
        padStatus == AssetModelDelivery.Status.TRANSFERRING ||
        padStatus == AssetModelDelivery.Status.PENDING
    val hfActive = baseState == "downloading"

    val progressPercent: Int? = when {
        isReady -> 100
        padActive -> delivery?.percent ?: 0
        hfActive -> downloadProgresses["base"] ?: 0
        else -> null
    }

    val statusText = when {
        isReady -> stringResource(R.string.onboarding_model_ready)
        isWaitingWifi -> stringResource(R.string.onboarding_model_waiting_wifi)
        progressPercent != null -> stringResource(R.string.onboarding_model_downloading)
        else -> stringResource(R.string.onboarding_model_preparing)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_offline_badge),
                style = MaterialTheme.typography.labelLarge,
                color = primaryEmerald,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = deepInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = softSlate,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(36.dp))

            // Model acquisition card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF211F24)),
                border = BorderStroke(
                    1.dp,
                    if (isReady) primaryEmerald else Color(0xFF49454F).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_model_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = deepInk,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isReady && progressPercent != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = primaryEmerald
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isReady) primaryEmerald else softSlate,
                            fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    AnimatedVisibility(visible = !isReady && progressPercent != null) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { (progressPercent ?: 0) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = primaryEmerald,
                                trackColor = Color(0xFF49454F)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${progressPercent ?: 0}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = softSlate
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.onboarding_wifi_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = softSlate,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_model_choice_note),
                style = MaterialTheme.typography.bodySmall,
                color = softSlate.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinish()
                },
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryEmerald,
                    contentColor = Color(0xFF1C1B1F),
                    disabledContainerColor = Color(0xFF49454F),
                    disabledContentColor = softSlate
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = softSlate
                )
            }
        }
    }
}
