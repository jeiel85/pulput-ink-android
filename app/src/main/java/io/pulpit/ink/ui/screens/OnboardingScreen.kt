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
import io.pulpit.ink.ui.viewmodel.SermonViewModel

/**
 * First-launch onboarding. Explains the offline-transcription value proposition
 * and guides the user through downloading the default (base) model once. The
 * download itself runs via [ModelDownloadService] (foreground, resumable), so
 * progress is reflected from the view model's download state.
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

    val downloadStates by viewModel.downloadState.collectAsState()
    val downloadProgresses by viewModel.downloadProgress.collectAsState()

    // Refresh once so an already-downloaded model is reflected immediately.
    LaunchedEffect(Unit) {
        viewModel.refreshWhisperStates()
    }

    val baseState = downloadStates["base"] ?: "not_downloaded"
    val isReady = baseState == "downloaded"
    val isDownloading = baseState == "downloading"
    val progressPercent = downloadProgresses["base"] ?: 0

    val statusText = when {
        isReady -> stringResource(R.string.onboarding_model_ready)
        isDownloading -> stringResource(R.string.onboarding_model_downloading)
        else -> stringResource(R.string.onboarding_model_intro)
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
                        if (isDownloading) {
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

                    AnimatedVisibility(visible = isDownloading) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = primaryEmerald,
                                trackColor = Color(0xFF49454F)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = softSlate
                            )
                        }
                    }

                    // Download trigger (only before the model is ready / downloading)
                    AnimatedVisibility(visible = !isReady && !isDownloading) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.ensureBaseModelForOnboarding() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryEmerald,
                                    contentColor = Color(0xFF1C1B1F)
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_download),
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                    if (isReady) viewModel.selectWhisperModel("base")
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
