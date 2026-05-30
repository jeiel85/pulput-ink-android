package io.pulpit.ink.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pulpit.ink.R
import io.pulpit.ink.data.model.SermonJob
import io.pulpit.ink.data.model.SermonSegment
import io.pulpit.ink.ui.viewmodel.SermonViewModel
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import io.pulpit.ink.ui.theme.bounceClickable
import androidx.activity.compose.BackHandler
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    viewModel: SermonViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeJob by viewModel.activeSermonJob.collectAsState()
    val segments by viewModel.activeSermonSegments.collectAsState()

    // Sound Player States
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val playbackDuration by viewModel.playbackDuration.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    // System back should follow the same path as the toolbar back button:
    // clear the active selection, then pop one depth back to Home — never
    // skip straight out of the screen with a stale selection lingering.
    BackHandler {
        viewModel.selectSermon(null)
        onNavigateBack()
    }

    // Automatically shift to Transcript tab once the sermon transcription completes successfully
    LaunchedEffect(activeJob) {
        val job = activeJob
        if (job != null && job.status == "Done" && pagerState.currentPage == 0) {
            pagerState.animateScrollToPage(1)
        }
    }

    val tabTitles = listOf(
        stringResource(R.string.tab_brief),
        stringResource(R.string.tab_transcript),
        stringResource(R.string.tab_outline),
        stringResource(R.string.tab_actions)
    )

    // Elegant Dark Theme Palette
    val deepInk = Color(0xFFE6E1E5)
    val parchmentBackground = Color(0xFF1C1B1F)
    val primaryEmerald = Color(0xFFD0BCFF)
    val primaryIndigo = Color(0xFFD0BCFF)
    val softSlate = Color(0xFFCAC4D0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeJob?.title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = deepInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.selectSermon(null)
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = deepInk
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = parchmentBackground)
            )
        },
        containerColor = parchmentBackground
    ) { innerPadding ->
        if (activeJob == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primaryEmerald)
            }
        } else {
            val job = activeJob!!
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Slideway navigation tabs
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = parchmentBackground,
                    contentColor = primaryEmerald
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (pagerState.currentPage == index) primaryIndigo else softSlate,
                                    modifier = Modifier.testTag("detail_tab_$index")
                                )
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> BriefingPlayerTab(
                            job = job,
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            duration = playbackDuration,
                            onPlayToggle = { viewModel.togglePlayback(job.audioPath) },
                            onSeek = { viewModel.seekPlayback(it) },
                            viewModel = viewModel
                        )
                        1 -> TranscriptTab(
                            jobId = job.id,
                            segments = segments,
                            jobStatus = job.status,
                            viewModel = viewModel,
                            onEditSegment = { id, text -> viewModel.editSegment(id, text) }
                        )
                        2 -> OutlineTab(
                            job = job,
                            onRegenerate = { viewModel.runRegenerateOutline(job.id) }
                        )
                        3 -> ActionsTab(
                            job = job,
                            segments = segments,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/* =========================================================================
   TAB 1: BRIEFING & AUDIO PLAYER
   ========================================================================= */
@Composable
fun BriefingPlayerTab(
    job: SermonJob,
    isPlaying: Boolean,
    progress: Int,
    duration: Int,
    onPlayToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    viewModel: SermonViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val emerald = Color(0xFFD0BCFF)
    val textDeep = Color(0xFFE6E1E5)
    val textMuted = Color(0xFFCAC4D0)
    val isKo = java.util.Locale.getDefault().language == "ko"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error Display Card (If failed)
        if (job.status == "Failed" && !job.errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E1E)),
                border = BorderStroke(1.dp, Color(0xFFF2B8B5).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFF2B8B5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isKo) "상세 오류 내용" else "Detailed Error Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF2B8B5)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = job.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCAC4D0)
                        )
                    }
                }
            }
        }

        // Metadata metrics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.sermon_metadata_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textDeep
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetaBadge(label = stringResource(R.string.metadata_length), value = formatDuration(job.durationSec), icon = Icons.Default.AccessTime)
                    MetaBadge(label = stringResource(R.string.metadata_date), value = formatTimestamp(job.createdAt), icon = Icons.Default.CalendarToday)
                }

                if (!job.bibleRefs.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.scriptural_badges),
                        style = MaterialTheme.typography.bodySmall,
                        color = textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        job.bibleRefs.split(",").forEach { ref ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF49454F).copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = ref.trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textDeep
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1.5 Transcription Retry Button
        if (job.status == "Failed" || job.status == "Done") {
            var showConfirmDialog by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    if (job.status == "Done") {
                        showConfirmDialog = true
                    } else {
                        viewModel.triggerTranscription(job.id)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (job.status == "Failed") MaterialTheme.colorScheme.error else emerald,
                    contentColor = if (job.status == "Failed") Color.White else Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("re_transcribe_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKo) "필기 다시 시작" else "Restart Transcription",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = {
                        Text(
                            text = if (isKo) "필기 다시 시작" else "Restart Transcription",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Text(
                            text = if (isKo) "이미 완성된 설교 필기 노트와 개요가 존재합니다. 다시 필기 작성을 시작하면 기존 필기 노트 정보와 사용자 수정 내역이 모두 초기화되고 새로 분석을 수행합니다. 계속하시겠습니까?"
                                   else "A completed set of notes and outline already exist. Re-transcribing will reset all existing note text, outline details, and manual corrections. Do you want to proceed?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                viewModel.triggerTranscription(job.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = emerald)
                        ) {
                            Text(
                                text = if (isKo) "재시도 시작" else "Start Retry",
                                color = Color(0xFF381E72)
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text(text = if (isKo) "취소" else "Cancel")
                        }
                    }
                )
            }
        }

        // Custom high-contrast MediaPlayer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audio_player_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.live_recording_file),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCAC4D0)
                        )
                        Text(
                            text = job.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val file = java.io.File(job.audioPath)
                            if (file.exists()) {
                                Toast.makeText(context, context.getString(R.string.audio_source_ok), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.audio_source_error), Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFCAC4D0)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timeline scrub slider
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = emerald,
                        activeTrackColor = emerald,
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag("playback_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatAudioTime(progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                    Text(
                        text = formatAudioTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons deck
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(emerald)
                        .bounceClickable { onPlayToggle() }
                        .testTag("playback_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Summary Paragraph Section (AI generated)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = emerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.executive_review_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textDeep
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = job.summary ?: stringResource(R.string.summary_compiling),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCAC4D0),
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                
                val isKo = java.util.Locale.getDefault().language == "ko"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF211F24))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = emerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKo) "상단의 \'필기 노트\' 탭에서 전체 텍스트 확인 및 편집이 가능합니다." 
                               else "View and edit full text in the \'Notes\' tab above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = emerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MetaBadge(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFCAC4D0), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCAC4D0))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
        }
    }
}

/* =========================================================================
   TAB 2: TRANSCRIPT EDITOR
   ========================================================================= */
@Composable
fun TranscriptTab(
    jobId: String,
    segments: List<SermonSegment>,
    jobStatus: String,
    viewModel: SermonViewModel,
    onEditSegment: (Int, String) -> Unit
) {
    var editSegmentDialogFor by remember { mutableStateOf<SermonSegment?>(null) }
    val emerald = Color(0xFFD0BCFF)
    val textDeep = Color(0xFFE6E1E5)
    val textMuted = Color(0xFFCAC4D0)
    
    val transcriptionProgresses by viewModel.transcriptionProgress.collectAsState()

    if (jobStatus == "Transcribing") {
        val transcribingProgress = transcriptionProgresses[jobId] ?: 40
        val isKo = java.util.Locale.getDefault().language == "ko"
        val statusText = when {
            transcribingProgress < 20 -> if (isKo) "필기 준비 중..." else "Preparing..."
            transcribingProgress < 30 -> if (isKo) "오디오 디코딩 중..." else "Decoding audio..."
            transcribingProgress < 40 -> if (isKo) "필기 엔진 모델 로딩 중..." else "Loading transcription model..."
            transcribingProgress < 75 -> {
                val nativePct = ((transcribingProgress - 40) * 100 / 35).coerceIn(0, 100)
                if (isKo) "음성 필기 분석 중... ($nativePct%)" else "Analyzing audio speech... ($nativePct%)"
            }
            else -> if (isKo) "맞춤법 교정 및 요약/개요서 가공 중..." else "Proofreading & summarizing notes..."
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient pulsing aura behind the glass card
            val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
            val glowScaleState = infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow_scale"
            )
            val glowAlphaState = infiniteTransition.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow_alpha"
            )

            // Cache brushes to prevent memory allocations during draw and layout passes
            val radialGradientBrush = remember(emerald) {
                Brush.radialGradient(
                    colors = listOf(emerald, Color.Transparent)
                )
            }
            val borderBrush = remember {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.03f)
                    )
                )
            }

            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        scaleX = glowScaleState.value
                        scaleY = glowScaleState.value
                        alpha = glowAlphaState.value
                    }
            ) {
                drawCircle(brush = radialGradientBrush)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B24).copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, borderBrush),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = transcribingProgress / 100f,
                        color = emerald,
                        trackColor = Color(0xFF49454F).copy(alpha = 0.5f),
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isKo) "전체 필기 및 AI 가공 진행률: $transcribingProgress%" else "Overall progress: $transcribingProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = textMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = transcribingProgress / 100f,
                        color = emerald,
                        trackColor = Color(0xFF49454F).copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelSermonTranscription(jobId) },
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClickable {
                                viewModel.cancelSermonTranscription(jobId)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKo) "필기 분석 중단" else "Cancel Transcription",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    } else if (segments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_transcript),
                style = MaterialTheme.typography.bodyMedium,
                color = textMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(segments, key = { it.id }) { segment ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClickable { editSegmentDialogFor = segment }
                        .testTag("segment_box_${segment.id}"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = emerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = segment.speaker ?: stringResource(R.string.speaker_default),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textDeep
                                )
                            }
                            Text(
                                text = "${formatAudioTime((segment.startSec * 1000).toInt())} - ${formatAudioTime((segment.endSec * 1000).toInt())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = textMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textDeep,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = primaryIndigoColor(),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.tap_to_edit),
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryIndigoColor()
                            )
                        }
                    }
                }
            }
        }
    }

    // Segment dialogue editor
    editSegmentDialogFor?.let { segment ->
        var tempText by remember { mutableStateOf(segment.text) }
        AlertDialog(
            onDismissRequest = { editSegmentDialogFor = null },
            title = {
                Text(
                    text = stringResource(R.string.edit_segment_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("segment_editor_field"),
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = emerald)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditSegment(segment.id, tempText)
                        editSegmentDialogFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = emerald)
                ) {
                    Text(
                        text = stringResource(R.string.save_modifications),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF381E72)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { editSegmentDialogFor = null }) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

/* =========================================================================
   TAB 3: SERMON OUTLINE (AI MARKDOWN RENDERING)
   ========================================================================= */
@Composable
fun OutlineTab(job: SermonJob, onRegenerate: () -> Unit) {
    val scrollState = rememberScrollState()
    val textDeep = Color(0xFFE6E1E5)
    val textMuted = Color(0xFFCAC4D0)
    val emerald = Color(0xFFD0BCFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.homiletics_draft_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textDeep
            )
            Button(
                onClick = onRegenerate,
                colors = ButtonDefaults.buttonColors(containerColor = emerald, contentColor = Color(0xFF381E72)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.re_compile),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("markdown_outline_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val markdownText = job.outline
                if (markdownText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_outline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // Fast Markdown parsing of headings and bulk passages
                    val lines = markdownText.split("\n")
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("# ") -> {
                                Text(
                                    text = trimmed.substring(2),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textDeep,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            trimmed.startsWith("## ") -> {
                                Text(
                                    text = trimmed.substring(3),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryIndigoColor(),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            trimmed.startsWith("### ") -> {
                                Text(
                                    text = trimmed.substring(4),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textDeep,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            trimmed.startsWith("> ") -> {
                                InfoRowQuoteBlock(text = trimmed.substring(2))
                            }
                            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                Row(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                                    Text("• ", fontWeight = FontWeight.Bold, color = emerald)
                                    Text(
                                        text = trimmed.substring(2),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textDeep,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                            trimmed.isNotEmpty() -> {
                                Text(
                                    text = trimmed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textDeep,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            else -> {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRowQuoteBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2B2930).copy(alpha = 0.5f))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFD0BCFF))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE6E1E5),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/* =========================================================================
   TAB 4: AI ACTIONS ACTIONS & EXPORTS PANEL
   ========================================================================= */
@Composable
fun ActionsTab(
    job: SermonJob,
    segments: List<SermonSegment>,
    viewModel: SermonViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val textDeep = Color(0xFFE6E1E5)
    val emerald = Color(0xFFD0BCFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.ai_ops_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textDeep
        )

        // Correction trigger segment
        ActionCard(
            title = stringResource(R.string.theological_proofreading_title),
            desc = stringResource(R.string.theological_proofreading_desc),
            icon = Icons.Default.AutoFixHigh,
            accentColor = emerald,
            onClick = { viewModel.runAutoRefCorrection(job.id) },
            tag = "correct_refs_button"
        )

        ActionCard(
            title = stringResource(R.string.recompile_outline_title),
            desc = stringResource(R.string.recompile_outline_desc),
            icon = Icons.Default.Analytics,
            accentColor = emerald,
            onClick = { viewModel.runRegenerateOutline(job.id) },
            tag = "recompile_outline_button"
        )

        HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.4f))

        Text(
            text = stringResource(R.string.exports_channels_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textDeep
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = {
                        val fulltext = segments.joinToString("\n\n") { it.text }
                        copyToClipboard(context, context.getString(R.string.copy_transcript), fulltext)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("copy_transcript_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = emerald, contentColor = Color(0xFF381E72)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.copy_transcript),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = {
                        val outline = job.outline ?: "No outline text compiled."
                        copyToClipboard(context, context.getString(R.string.copy_outline), outline)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("copy_outline_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = emerald, contentColor = Color(0xFF381E72)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.copy_outline),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Button(
            onClick = {
                val shareText = """
                    === sermon title: ${job.title} ===
                    
                    === summary ===
                    ${job.summary}
                    
                    === outline ===
                    ${job.outline}
                """.trimIndent()
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, job.title)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Sermon Draft via..."))
            },
            modifier = Modifier.fillMaxWidth().testTag("share_sermon_button"),
            colors = ButtonDefaults.buttonColors(containerColor = primaryIndigoColor(), contentColor = Color(0xFF381E72)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.share_package),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable { onClick() }
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCAC4D0),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.copied_toast, label), Toast.LENGTH_SHORT).show()
}

// Utility style lookups
fun primaryIndigoColor() = Color(0xFFD0BCFF)

fun formatAudioTime(ms: Int): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
