package com.tkno.miora.ui.send

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.miora.R
import com.tkno.miora.model.*
import com.tkno.miora.ui.icon.Files
import com.tkno.miora.ui.icon.LibraryMusic
import com.tkno.miora.util.FileThumbnailHelper
import com.tkno.miora.util.rememberThumbnail
import com.tkno.miora.util.rememberVideoDuration

@Composable
fun SendTransferSessionView(
    session: TransferSessionState,
    useClassicTaskbar: Boolean,
    isReceiver: Boolean = false,
    onCloseSession: () -> Unit,
    onSendAdditionalFiles: ((List<MediaItem>) -> Unit)? = null
) {
    val context = LocalContext.current
    var isAddMenuExpanded by remember { mutableStateOf(false) }
    var additionalPendingFiles by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Launcher for additional files picked during session
    val handleAdditionalUris: (List<Uri>) -> Unit = { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = resolveUrisToMediaItems(context, uris)
            val maxAllowed = if (session.protocol == TransferProtocol.BLUETOOTH) 100 else 500
            val currentTotal = session.totalFiles + additionalPendingFiles.size
            val existingUris = additionalPendingFiles.map { it.uri }.toSet()
            val uniqueNewItems = items.filter { it.uri !in existingUris }
            val availableSlots = (maxAllowed - currentTotal).coerceAtLeast(0)

            if (uniqueNewItems.size > availableSlots) {
                val toastMsg = if (session.protocol == TransferProtocol.BLUETOOTH) {
                    context.getString(R.string.max_files_limit_bt, 100)
                } else {
                    context.getString(R.string.max_files_limit_wifi, 500)
                }
                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
            }

            val itemsToAdd = uniqueNewItems.take(availableSlots)
            if (itemsToAdd.isNotEmpty()) {
                additionalPendingFiles = additionalPendingFiles + itemsToAdd
                Toast.makeText(context, "${itemsToAdd.size} file(s) selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = handleAdditionalUris
    )

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = handleAdditionalUris
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = if (useClassicTaskbar) 120.dp else 220.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (session.isFinished) {
                                    onCloseSession()
                                } else {
                                    if (isReceiver) {
                                        TransferController.cancelReceiveTransfer()
                                    } else {
                                        TransferController.cancelSendTransfer()
                                    }
                                    onCloseSession()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isReceiver) stringResource(R.string.receiving_files) else stringResource(R.string.sending_files),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (session.status) {
                                    TransferStatus.CONNECTING -> stringResource(R.string.connecting_to_receiver)
                                    TransferStatus.TRANSFERRING -> session.currentFileName.ifBlank { "Streaming..." }
                                    TransferStatus.COMPLETED -> stringResource(R.string.transfer_completed)
                                    TransferStatus.FAILED -> stringResource(R.string.transfer_failed)
                                    TransferStatus.CANCELLED -> stringResource(R.string.cancel)
                                    else -> session.protocol.displayName
                                },
                                fontSize = 13.sp,
                                color = if (session.status == TransferStatus.COMPLETED) Color(0xFF2E7D32)
                                        else if (session.status == TransferStatus.FAILED || session.status == TransferStatus.CANCELLED) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Done / Cancel Action Button
                    if (session.isFinished) {
                        FilledIconButton(
                            onClick = onCloseSession,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (session.status == TransferStatus.COMPLETED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (session.status == TransferStatus.COMPLETED) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Done",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (isReceiver) {
                                    TransferController.cancelReceiveTransfer()
                                } else {
                                    TransferController.cancelSendTransfer()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Top Progress Bar & Live Stats Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Progress Bar
                        val animatedProgress by animateFloatAsState(
                            targetValue = session.progress,
                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                            label = "transfer_progress"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (session.isFinished) "100%" else "${(animatedProgress * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = session.protocol.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = if (session.status == TransferStatus.COMPLETED) Color(0xFF2E7D32)
                                        else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // 3 Key Stats: Speed | Transferred Volume | Files Count
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Transfer Speed
                            TransferStatBox(
                                icon = Icons.Outlined.Speed,
                                value = if (session.status == TransferStatus.CONNECTING) "--" else session.formattedSpeed,
                                label = "Speed",
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                modifier = Modifier.height(36.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // 2. Data Volume Sent vs Total
                            TransferStatBox(
                                icon = Icons.Outlined.DataUsage,
                                value = session.formattedBytesProgress,
                                label = "Transferred",
                                modifier = Modifier.weight(1.3f)
                            )

                            VerticalDivider(
                                modifier = Modifier.height(36.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // 3. Sent File Count (1/5, 2/5...)
                            TransferStatBox(
                                icon = Icons.Outlined.FolderCopy,
                                value = session.formattedCountProgress,
                                label = "Files",
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }

            // Expandable Category Buttons (Photo, Video, Audio, Document, File) - Senders Only
            if (!isReceiver) {
                item {
                    AnimatedVisibility(
                        visible = isAddMenuExpanded,
                    enter = expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn() + scaleIn(initialScale = 0.96f),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 180)
                    ) + fadeOut() + scaleOut(targetScale = 0.96f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.add_more_files),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { isAddMenuExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Row 1: Photos, Videos, Audios (3 items)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MediaPickerChip(
                                    icon = Icons.Outlined.PhotoLibrary,
                                    label = stringResource(R.string.pick_images),
                                    modifier = Modifier.weight(1f),
                                    onClick = { mediaLauncher.launch("image/*") }
                                )
                                MediaPickerChip(
                                    icon = Icons.Outlined.VideoLibrary,
                                    label = stringResource(R.string.pick_videos),
                                    modifier = Modifier.weight(1f),
                                    onClick = { mediaLauncher.launch("video/*") }
                                )
                                MediaPickerChip(
                                    icon = LibraryMusic,
                                    label = stringResource(R.string.pick_audio),
                                    modifier = Modifier.weight(1f),
                                    onClick = { mediaLauncher.launch("audio/*") }
                                )
                            }

                            // Row 2: Documents, Files (2 items)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MediaPickerChip(
                                    icon = Icons.Outlined.Description,
                                    label = stringResource(R.string.pick_documents),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        docLauncher.launch(
                                            arrayOf(
                                                "application/pdf",
                                                "application/msword",
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "application/vnd.ms-excel",
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "application/vnd.ms-powerpoint",
                                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                                "text/*",
                                                "application/rtf",
                                                "application/epub+zip",
                                                "application/zip"
                                            )
                                        )
                                    }
                                )
                                MediaPickerChip(
                                    icon = Files,
                                    label = stringResource(R.string.pick_files),
                                    modifier = Modifier.weight(1f),
                                    onClick = { mediaLauncher.launch("*/*") }
                                )
                            }

                            // Action button if user selected additional files
                            if (additionalPendingFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        onSendAdditionalFiles?.invoke(additionalPendingFiles)
                                        additionalPendingFiles = emptyList()
                                        isAddMenuExpanded = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send ${additionalPendingFiles.size} Added File(s)")
                                }
                            }
                        }
                    }
                }
            }
            }

            // Transferred Files Sequential Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isReceiver) stringResource(R.string.received_items_header) else stringResource(R.string.sent_items_header),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${session.completedRecords.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 1. In-Progress Streaming Item Card (Shows active file currently sending/receiving)
            if (session.status == TransferStatus.TRANSFERRING && session.currentFileName.isNotBlank()) {
                item(key = "active_transfer_${session.currentFileName}") {
                    ActiveTransferItemCard(
                        fileName = session.currentFileName,
                        progress = session.progress,
                        speedMbps = session.speedMbps,
                        isReceiver = isReceiver
                    )
                }
            }

            // 2. Sequential Completed Items
            if (session.completedRecords.isNotEmpty()) {
                items(
                    items = session.completedRecords,
                    key = { it.id },
                    contentType = { "transfer_record" }
                ) { record ->
                    SessionItemCard(
                        record = record,
                        onOpen = { FileThumbnailHelper.openFile(context, record) },
                        onShare = { FileThumbnailHelper.shareFile(context, record) }
                    )
                }
            } else if (session.status != TransferStatus.TRANSFERRING) {
                // Empty or Failed / Interrupted State
                item(key = "session_empty_state") {
                    if (session.status == TransferStatus.FAILED || (session.isFinished && !session.isSuccess)) {
                        TransferFailureCard(onClose = onCloseSession)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (session.status == TransferStatus.CONNECTING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isReceiver) stringResource(R.string.waiting_for_sender) else stringResource(R.string.connecting_to_receiver),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = if (isReceiver) stringResource(R.string.no_files_received_yet) else stringResource(R.string.no_files_sent_yet),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Square Plus Button above Taskbar (Senders Only)
        if (!isReceiver) {
            SquareAddButton(
                isExpanded = isAddMenuExpanded,
                onClick = { isAddMenuExpanded = !isAddMenuExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (!useClassicTaskbar) {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 90.dp, end = 22.dp)
                        } else {
                            Modifier.padding(bottom = 20.dp, end = 22.dp)
                        }
                    )
            )
        }
    }
}

@Composable
fun TransferStatBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SquareAddButton(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isExpanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier.size(size)
    ) {
        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 45f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "fab_rotation"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = if (isExpanded) "Close" else "Add",
                modifier = Modifier
                    .size(26.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
fun SessionItemCard(
    record: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val thumbnail = rememberThumbnail(record)
    val videoDuration = rememberVideoDuration(record)

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
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Thumbnail Preview
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Overlay Tag at Bottom-End
                if (record.isVideo) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            if (!videoDuration.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = videoDuration,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else if (record.isImage) {
                    val ext = record.extension.uppercase()
                    if (ext.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = ext,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    val ext = record.extension.uppercase()
                    if (ext.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = ext,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Information Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = record.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isIncoming = record.direction == TransferDirection.RECEIVE
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isIncoming) stringResource(R.string.received_files) else stringResource(R.string.sent_files),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )

                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = record.formattedSize,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Share action button
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveTransferItemCard(
    fileName: String,
    progress: Float,
    speedMbps: Float,
    isReceiver: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val isVideo = ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v")
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg")
    val isAudio = ext in listOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus", "wma")

    val iconVector = when {
        isVideo -> Icons.Outlined.VideoLibrary
        isImage -> Icons.Outlined.PhotoLibrary
        isAudio -> LibraryMusic
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isReceiver) stringResource(R.string.tab_receive) else stringResource(R.string.tab_send),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (speedMbps > 0f) {
                            Text(
                                text = "• ${formatSpeed(speedMbps)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

@Composable
fun TransferFailureCard(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.transfer_failed),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.transfer_interrupted),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onClose,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.transfer_done))
            }
        }
    }
}

