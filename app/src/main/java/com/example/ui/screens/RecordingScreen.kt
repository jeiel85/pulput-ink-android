package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.SermonViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: SermonViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDurationSec.collectAsState()

    var sermonTitle by remember { mutableStateOf("") }
    var sermonFocusTopic by remember { mutableStateOf("") }

    // Elegant Dark Theme Palette
    val deepInk = Color(0xFFE6E1E5)
    val parchmentBackground = Color(0xFF1C1B1F)
    val primaryEmerald = Color(0xFFD0BCFF)
    val recordingRed = Color(0xFFFF5252)
    val softSlate = Color(0xFFCAC4D0)

    // Pulsating animation scale for mic icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pulpit Voice Capture", fontWeight = FontWeight.Bold, color = deepInk) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = deepInk
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = parchmentBackground)
            )
        },
        containerColor = parchmentBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Info Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = if (isRecording) "Recording Sermon Audio..." else "Configure Sermon Preach Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRecording) recordingRed else deepInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isRecording) "Microphone is live. Pray, preach, and teach." else "Provide a title and theme focus for transcription. Standard themes will fall back automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = softSlate,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Interactive Forms (Shown only when not active recording)
                Crossfade(targetState = isRecording, label = "forms") { recordingState ->
                    if (!recordingState) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = sermonTitle,
                                onValueChange = { sermonTitle = it },
                                label = { Text("Sermon Draft Title") },
                                placeholder = { Text("e.g., The Beauty of Assurance") },
                                maxLines = 1,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sermon_title_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = deepInk,
                                    unfocusedTextColor = deepInk,
                                    focusedBorderColor = primaryEmerald,
                                    unfocusedBorderColor = Color(0xFF49454F),
                                    focusedContainerColor = Color(0xFF2B2930),
                                    unfocusedContainerColor = Color(0xFF2B2930),
                                    focusedLabelColor = primaryEmerald,
                                    unfocusedLabelColor = softSlate,
                                    focusedPlaceholderColor = softSlate,
                                    unfocusedPlaceholderColor = softSlate
                                )
                            )

                            OutlinedTextField(
                                value = sermonFocusTopic,
                                onValueChange = { sermonFocusTopic = it },
                                label = { Text("Theological Core Theme (Dynamic AI Cue)") },
                                placeholder = { Text("e.g. Grace, Forgiveness, Prodigal Son...") },
                                maxLines = 1,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sermon_topic_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = deepInk,
                                    unfocusedTextColor = deepInk,
                                    focusedBorderColor = primaryEmerald,
                                    unfocusedBorderColor = Color(0xFF49454F),
                                    focusedContainerColor = Color(0xFF2B2930),
                                    unfocusedContainerColor = Color(0xFF2B2930),
                                    focusedLabelColor = primaryEmerald,
                                    unfocusedLabelColor = softSlate,
                                    focusedPlaceholderColor = softSlate,
                                    unfocusedPlaceholderColor = softSlate
                                )
                            )
                        }
                    } else {
                        // Display visual real-time waveform bars during recording
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedWaveform()
                        }
                    }
                }

                // Time Indicator Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        text = formatDuration(recordingDuration),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isRecording) recordingRed else deepInk,
                        modifier = Modifier.testTag("recording_time_text")
                    )
                    
                    if (isRecording) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(recordingRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "REC LIVE (MIC-HD)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = recordingRed
                            )
                        }
                    }
                }

                // Call To Action (Trigger Microphone Controls)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    if (!isRecording) {
                        // Outer ring of start record
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(primaryEmerald.copy(alpha = 0.15f))
                                .clickable { viewModel.startRecording() }
                                .testTag("mic_trigger_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(primaryEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Start recording voice",
                                    tint = Color(0xFF381E72),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tap to start capturing pulpit audio",
                            fontSize = 13.sp,
                            color = softSlate,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // Recording controllers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel / trash button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = {
                                        viewModel.cancelRecording()
                                        onNavigateBack()
                                    },
                                    containerColor = Color(0xFF49454F),
                                    contentColor = Color(0xFFE6E1E5),
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp).testTag("cancel_mic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Trash recording"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Discard", fontSize = 12.sp, color = softSlate)
                            }

                            // Big stop button with pulsing effect
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .scale(pulseScale),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(86.dp)
                                        .clip(CircleShape)
                                        .background(recordingRed.copy(alpha = 0.2f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(recordingRed)
                                        .clickable {
                                            viewModel.stopRecordingAndSave(sermonTitle, sermonFocusTopic)
                                            onNavigateBack()
                                        }
                                        .testTag("stop_mic_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop recording voice",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Complete / Transcribe immediately
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = {
                                        viewModel.stopRecordingAndSave(sermonTitle, sermonFocusTopic)
                                        onNavigateBack()
                                    },
                                    containerColor = primaryEmerald,
                                    contentColor = Color(0xFF381E72),
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp).testTag("save_mic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Stop and Save draft"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Transcribe", fontSize = 12.sp, color = softSlate)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom styled animated Canvas vector drawing to draw real-looking audio waveform lines
 */
@Composable
fun AnimatedWaveform() {
    val emerald = Color(0xFFD0BCFF)
    val heights = listOf(15f, 45f, 25f, 75f, 10f, 60f, 35f, 90f, 20f, 70f, 40f, 85f, 15f, 50f, 30f)
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val numBars = heights.size
        val barGap = 12f
        val barWidth = (width - (numBars - 1) * barGap) / numBars

        for (i in 0 until numBars) {
            // Apply sine wave transformation based on index and phaseShift to animate height beautifully
            val angleRadians = Math.toRadians((i * (360f / numBars) + phaseShift).toDouble())
            val baseFactor = heights[i] / 100f
            val modulation = (Math.sin(angleRadians).toFloat() + 1f) / 2f // normalize to 0..1
            val activeHeight = height * 0.8f * baseFactor * (0.4f + 0.6f * modulation)

            val x = i * (barWidth + barGap)
            val startY = midY - (activeHeight / 2f)
            val endY = midY + (activeHeight / 2f)

            drawLine(
                color = emerald,
                start = androidx.compose.ui.geometry.Offset(x, startY),
                end = androidx.compose.ui.geometry.Offset(x, endY),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
