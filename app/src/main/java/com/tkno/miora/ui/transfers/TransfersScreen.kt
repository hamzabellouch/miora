package com.tkno.miora.ui.transfers

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.miora.R
import com.tkno.miora.model.*

enum class TransferFilter {
    ALL, SENT, RECEIVED
}

@Composable
fun TransfersScreen() {
    val context = LocalContext.current
    val history by TransferHistoryManager.history.collectAsState()
    var selectedFilter by remember { mutableStateOf(TransferFilter.ALL) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredList = remember(history, selectedFilter) {
        when (selectedFilter) {
            TransferFilter.ALL -> history
            TransferFilter.SENT -> history.filter { it.direction == TransferDirection.SEND }
            TransferFilter.RECEIVED -> history.filter { it.direction == TransferDirection.RECEIVE }
        }
    }

    val totalSentBytes = remember(history) {
        history.filter { it.direction == TransferDirection.SEND }.sumOf { it.fileSize }
    }
    val totalReceivedBytes = remember(history) {
        history.filter { it.direction == TransferDirection.RECEIVE }.sumOf { it.fileSize }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.tab_transfers),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.transfers_history),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                }
            }

            // Summary Stats Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(
                            title = stringResource(R.string.sent_files),
                            volume = formatFileSize(totalSentBytes),
                            count = history.count { it.direction == TransferDirection.SEND },
                            icon = Icons.Outlined.Upload
                        )
                        VerticalDivider(
                            modifier = Modifier.height(44.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        StatItem(
                            title = stringResource(R.string.received_files),
                            volume = formatFileSize(totalReceivedBytes),
                            count = history.count { it.direction == TransferDirection.RECEIVE },
                            icon = Icons.Outlined.Download
                        )
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == TransferFilter.ALL,
                        onClick = { selectedFilter = TransferFilter.ALL },
                        label = { Text(stringResource(R.string.all_transfers)) }
                    )
                    FilterChip(
                        selected = selectedFilter == TransferFilter.SENT,
                        onClick = { selectedFilter = TransferFilter.SENT },
                        label = { Text(stringResource(R.string.sent_files)) }
                    )
                    FilterChip(
                        selected = selectedFilter == TransferFilter.RECEIVED,
                        onClick = { selectedFilter = TransferFilter.RECEIVED },
                        label = { Text(stringResource(R.string.received_files)) }
                    )
                }
            }

            // Empty state
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_transfers),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { record ->
                    TransferRecordCard(
                        record = record,
                        onDelete = { TransferHistoryManager.removeRecord(record.id) },
                        onShare = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Transferred via Miora: ${record.fileName} (${record.formattedSize})")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share with"))
                        }
                    )
                }
            }
        }

        // Clear Confirmation Dialog
        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                title = { Text(stringResource(R.string.clear_history)) },
                text = { Text(stringResource(R.string.clear_history_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            TransferHistoryManager.clearAll()
                            showClearConfirmDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.clear_history))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun StatItem(
    title: String,
    volume: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = volume,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count file(s)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun TransferRecordCard(
    record: TransferRecord,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val (iconVector, containerColor, contentColor) = when {
        record.isVideo -> Triple(
            Icons.Outlined.Videocam,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        record.isImage -> Triple(
            Icons.Outlined.Image,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        record.isAudio -> Triple(
            Icons.Outlined.AudioFile,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            Icons.AutoMirrored.Outlined.InsertDriveFile,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.formattedSize,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
