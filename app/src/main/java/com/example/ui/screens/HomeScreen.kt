package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SermonJob
import com.example.ui.viewmodel.SermonViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: SermonViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sermonJobs by viewModel.sermonJobs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf<SermonJob?>(null) }

    // Elegant Dark Theme Palette
    val deepInk = Color(0xFFE6E1E5) // Clean premium white/silver
    val parchmentBackground = Color(0xFF1C1B1F) // Deep obsidian black
    val primaryEmerald = Color(0xFFD0BCFF) // Gorgeous warm theme lavender
    val softSlate = Color(0xFFCAC4D0) // Muted lavender grey

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = primaryEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pulpit Ink",
                                fontWeight = FontWeight.Bold,
                                color = deepInk,
                                modifier = Modifier.testTag("app_title")
                            )
                        }
                        Text(
                            "Mobile Sermon Transcription Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = softSlate,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = parchmentBackground,
                    scrolledContainerColor = parchmentBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecord,
                containerColor = primaryEmerald,
                contentColor = Color(0xFF381E72),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("record_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Recording"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Sermon", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = parchmentBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // High-Contrast Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
                placeholder = { Text("Search sermons, scriptures, or tags...", color = softSlate) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = softSlate
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = softSlate
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = deepInk,
                    unfocusedTextColor = deepInk,
                    focusedBorderColor = primaryEmerald,
                    unfocusedBorderColor = Color(0xFF49454F),
                    focusedContainerColor = Color(0xFF2B2930),
                    unfocusedContainerColor = Color(0xFF2B2930)
                )
            )

            if (sermonJobs.isEmpty()) {
                // Refined empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF2B2930),
                            tonalElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Your Pulpit is Empty" else "No sermon matches found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = deepInk
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "Tap 'Record Sermon' below to capture audio, transcribe scripture alignments, and compile outlines using Gemini."
                            } else {
                                "Try adjusting your query or keywords to locate your transcribed documents."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = softSlate,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                ) {
                    items(sermonJobs, key = { it.id }) { job ->
                        SermonItemCard(
                            job = job,
                            onClick = {
                                viewModel.selectSermon(job.id)
                                onNavigateToDetail(job.id)
                            },
                            onLongClick = { showDeleteConfirmDialog = job }
                        )
                    }
                }
            }
        }
    }

    // Deletion Dialog
    showDeleteConfirmDialog?.let { job ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Purge Sermon Transcript?") },
            text = { Text("Are you sure you want to delete '${job.title}'? This action cannot be undone and will delete associated wave records, drafts, and outlines.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSermon(job.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SermonItemCard(
    job: SermonJob,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val inkBack = Color(0xFF2B2930)
    val borderCol = Color(0xFF49454F).copy(alpha = 0.3f)
    val emeraldAccent = Color(0xFFD0BCFF)
    val textDeep = Color(0xFFE6E1E5)
    val textMuted = Color(0xFFCAC4D0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("sermon_card_${job.id}"),
        colors = CardDefaults.cardColors(containerColor = inkBack),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title and Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textDeep,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = textMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimestamp(job.createdAt),
                            fontSize = 12.sp,
                            color = textMuted
                        )
                    }
                }

                // Dynamic Status Badge
                StatusBadge(status = job.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sermon Details Snippets
            if (!job.summary.isNullOrBlank()) {
                Text(
                    text = job.summary,
                    fontSize = 13.sp,
                    color = textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Duration and Scripture tags footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Duration icon",
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDuration(job.durationSec),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textMuted
                    )
                }

                // If Bible Verse keys detected, display prominent badge
                if (!job.bibleRefs.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF49454F).copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = emeraldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.bibleRefs.split(",").firstOrNull()?.trim() ?: "Refs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDeep
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (backColor, textColor, label) = when (status) {
        "Recording" -> Triple(Color(0xFF5A1616), Color(0xFFFFB4AB), "Recording")
        "Transcribing" -> Triple(Color(0xFF624000), Color(0xFFFFE082), "AI Ink Sync")
        "Done" -> Triple(Color(0xFF0F5234), Color(0xFFA7F3D0), "Transcribed")
        "Failed" -> Triple(Color(0xFF5A1616), Color(0xFFFFB4AB), "Error")
        else -> Triple(Color(0xFF49454F), Color(0xFFCAC4D0), "Queue")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(backColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDuration(durationSec: Double): String {
    val totalSeconds = durationSec.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
